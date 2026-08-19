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

        if (now >= fastBlockedUntil) {
            WindowNative.RawFrame fast = liveSession.captureFast(reuse);
            if (fast != null) {
                long sig = fast.signature();
                if (sig != lastFastSignature) {
                    lastFastSignature = sig;
                    lastFastChangedAt = now;
                    return fast;
                }
                if (lastFastChangedAt == 0L) lastFastChangedAt = now;

                // A truly static page is allowed to stay on the fast path. Probe PrintWindow only
                // occasionally so Chromium is never synchronously hammered 60-120 times/sec.
                if (now - lastFastChangedAt < 170L || now - lastReliableProbeAt < 180L) return fast;
                lastReliableProbeAt = now;
                WindowNative.RawFrame reliable = liveSession.capturePrint(fast.pixelsAbgr());
                if (reliable != null) {
                    if (reliable.signature() != sig) {
                        fastBlockedUntil = now + 1400L;
                        return reliable;
                    }
                    return reliable;
                }
                return fast;
            }
            fastBlockedUntil = now + 700L;
        }

        WindowNative.RawFrame reliable = liveSession.capturePrint(reuse);
        if (reliable != null) return reliable;
        return null;
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
