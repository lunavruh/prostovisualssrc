package dev.prostovisuals.client.spatial;

import com.sun.jna.platform.win32.GDI32Util;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** Capture source for one real top-level Windows application window. */
public final class WindowCaptureSource implements CaptureSource {
    private final long hwnd;
    private final String title;
    private volatile Rectangle bounds;
    private volatile BufferedImage cachedFrame;
    private final WindowNative.CaptureSession liveSession;
    private volatile long fastBlockedUntil;
    private volatile long lastFastSignature;
    private volatile long lastFastChangedAt;
    private volatile long lastReliableProbeAt;
    private volatile WindowNative.RawFrame lastDetailedFrame;

    public WindowCaptureSource(long hwnd, String title, Rectangle bounds) {
        this.hwnd = hwnd;
        this.title = title;
        this.bounds = new Rectangle(bounds);
        this.liveSession = new WindowNative.CaptureSession(hwnd);
    }

    public long hwnd() { return hwnd; }
    public Rectangle bounds() { return new Rectangle(bounds); }
    public void updateBounds(Rectangle bounds) { if (bounds != null) this.bounds = new Rectangle(bounds); }
    @Override public String id() { return "window:" + hwnd; }
    @Override public String name() { return title; }
    @Override public boolean isAvailable() { return WindowNative.isAlive(hwnd); }

    /**
     * Zero-BufferedImage hot path used by SpatialMonitor. Persistent native DC/bitmap/buffer resources
     * stay alive for the whole monitor session, then pixels go straight into NativeImage-ready ABGR.
     */
    public WindowNative.RawFrame captureRaw(int[] reuse) {
        Rectangle live = WindowNative.getBounds(hwnd);
        if (live != null) bounds = live;
        long now = System.currentTimeMillis();

        WindowNative.RawFrame candidate = null;
        if (now >= fastBlockedUntil) {
            candidate = liveSession.captureFast(reuse);
            if (candidate != null) {
                long sig = candidate.signature();
                if (sig != lastFastSignature) {
                    lastFastSignature = sig;
                    lastFastChangedAt = now;
                } else if (lastFastChangedAt == 0L) {
                    lastFastChangedAt = now;
                }
                // Probe the compositor-friendly path only when the fast image is stale/flat.
                if ((!WindowNative.isContentDetailedFrameAbgr(candidate.pixelsAbgr(), candidate.width(), candidate.height())
                        || now - lastFastChangedAt > 220L) && now - lastReliableProbeAt >= 180L) {
                    lastReliableProbeAt = now;
                    WindowNative.RawFrame print = liveSession.capturePrint(candidate.pixelsAbgr());
                    if (print != null) candidate = print;
                }
            } else {
                fastBlockedUntil = now + 500L;
            }
        }
        if (candidate == null) candidate = liveSession.capturePrint(reuse);

        if (candidate != null && WindowNative.isContentDetailedFrameAbgr(candidate.pixelsAbgr(), candidate.width(), candidate.height())) {
            lastDetailedFrame = copyFrame(candidate);
            return candidate;
        }

        // Try the independent GDI32Util/PrintWindow compatibility path before falling back to
        // desktop composition. This often captures Chromium client content when a persistent
        // BitBlt DC returns only the title bar + gray DirectComposition placeholder.
        BufferedImage reliable = capture();
        WindowNative.RawFrame reliableRaw = fromBufferedImage(reliable, reuse);
        if (reliableRaw != null && WindowNative.isContentDetailedFrameAbgr(
                reliableRaw.pixelsAbgr(), reliableRaw.width(), reliableRaw.height())) {
            lastDetailedFrame = copyFrame(reliableRaw);
            return reliableRaw;
        }

        // Chromium/Electron DirectComposition can hand GDI a uniform gray placeholder. If the
        // target itself is foreground, desktop composition contains the real pixels; grab them once.
        if (WindowNative.isForeground(hwnd)) {
            BufferedImage composed = WindowNative.captureForegroundCompositedWindow(hwnd);
            WindowNative.RawFrame converted = fromBufferedImage(composed, reuse);
            if (converted != null && WindowNative.isContentDetailedFrameAbgr(converted.pixelsAbgr(), converted.width(), converted.height())) {
                lastDetailedFrame = copyFrame(converted);
                return converted;
            }
        }

        // Never replace a valid browser frame with a gray compositor placeholder.
        WindowNative.RawFrame cached = lastDetailedFrame;
        if (cached != null) return copyFrame(cached);
        return candidate;
    }

    private static WindowNative.RawFrame fromBufferedImage(BufferedImage image, int[] reuse) {
        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) return null;
        int w = image.getWidth(), h = image.getHeight(), count = w * h;
        int[] p = reuse != null && reuse.length >= count ? reuse : new int[count];
        image.getRGB(0, 0, w, h, p, 0, w);
        for (int i = 0; i < count; i++) {
            int c = p[i];
            int a = (c >>> 24) & 255, r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
            p[i] = (a << 24) | (b << 16) | (g << 8) | r;
        }
        return new WindowNative.RawFrame(w, h, p, WindowNative.frameSignatureAbgr(p, w, h));
    }

    private static WindowNative.RawFrame copyFrame(WindowNative.RawFrame frame) {
        if (frame == null) return null;
        int count = frame.width() * frame.height();
        int[] copy = java.util.Arrays.copyOf(frame.pixelsAbgr(), count);
        return new WindowNative.RawFrame(frame.width(), frame.height(), copy, frame.signature());
    }

    /** Compatibility/fallback path for non-Spatial callers. */
    @Override
    public BufferedImage capture() {
        Rectangle live = WindowNative.getBounds(hwnd);
        if (live != null) bounds = live;
        BufferedImage image = null;
        try {
            image = GDI32Util.getScreenshot(WindowNative.hwnd(hwnd));
        } catch (Throwable ignored) {}
        if (!WindowNative.isUsefulFrame(image)) image = WindowNative.captureWindow(hwnd);
        if (WindowNative.isUsefulFrame(image)) {
            cachedFrame = image;
            return image;
        }
        return cachedFrame;
    }

    @Override
    public void close() {
        try { liveSession.close(); } catch (Throwable ignored) {}
    }
}
