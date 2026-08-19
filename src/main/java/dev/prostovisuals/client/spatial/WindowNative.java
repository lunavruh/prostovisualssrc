package dev.prostovisuals.client.spatial;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only Win32 discovery + capture/input routing helpers for Spatial Display. */
public final class WindowNative {
    private static final int PW_RENDERFULLCONTENT = 0x00000002;
    private static final int CWP_SKIPINVISIBLE = 0x0001;
    private static final int CWP_SKIPDISABLED = 0x0002;
    private static final int CWP_SKIPTRANSPARENT = 0x0004;
    private static final int GWL_EXSTYLE = -20;
    private static final long WS_EX_TOOLWINDOW = 0x00000080L;

    private static final DirectColorModel COLOR_MODEL = new DirectColorModel(24, 0x00FF0000, 0x0000FF00, 0x000000FF);
    private static final int[] BAND_MASKS = { COLOR_MODEL.getRedMask(), COLOR_MODEL.getGreenMask(), COLOR_MODEL.getBlueMask() };

    private static final Set<String> BLOCKED_APPS = Set.of(
            "applicationframehost", "systemsettings", "textinputhost", "shellexperiencehost",
            "startmenuexperiencehost", "searchhost", "widgets", "widgetservice", "lockapp",
            "dwm", "sihost", "taskhostw", "ctfmon", "runtimebroker"
    );
    private static final Set<String> BLOCKED_TITLES = Set.of(
            "program manager", "windows input experience", "default ime", "msctfime ui"
    );

    private WindowNative() {}

    private interface User32Extra extends StdCallLibrary {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean PrintWindow(WinDef.HWND hwnd, WinDef.HDC hdcBlt, int flags);
        WinDef.HDC GetWindowDC(WinDef.HWND hwnd);
        WinDef.HDC GetDC(WinDef.HWND hwnd);
        int ReleaseDC(WinDef.HWND hwnd, WinDef.HDC hdc);
        WinDef.HWND ChildWindowFromPointEx(WinDef.HWND parent, WinDef.POINT point, int flags);
        boolean ScreenToClient(WinDef.HWND hwnd, WinDef.POINT point);
        boolean ClientToScreen(WinDef.HWND hwnd, WinDef.POINT point);
        boolean IsWindow(WinDef.HWND hwnd);
    }

    public record WindowInfo(long hwnd, String app, String title, Rectangle bounds) {
        public String displayName() {
            String a = app == null || app.isBlank() ? "App" : app;
            return a + "  •  " + title;
        }
    }

    public record InputTarget(long hwnd, int clientX, int clientY, int screenX, int screenY) {}

    public static List<WindowInfo> listCapturableWindows() {
        if (!isWindows()) return List.of();
        List<WindowInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        User32 u = User32.INSTANCE;
        long ownPid = ProcessHandle.current().pid();

        try {
            u.EnumWindows((hwnd, data) -> {
                try {
                    if (hwnd == null || hwnd.getPointer() == null || !u.IsWindowVisible(hwnd)) return true;

                    int len = u.GetWindowTextLength(hwnd);
                    if (len <= 0) return true;
                    char[] buf = new char[Math.min(4096, len + 1)];
                    int read = u.GetWindowText(hwnd, buf, buf.length);
                    if (read <= 0) return true;
                    String title = sanitize(new String(buf, 0, read));
                    if (title.isBlank()) return true;

                    WinDef.RECT rect = new WinDef.RECT();
                    if (!u.GetWindowRect(hwnd, rect)) return true;
                    int w = rect.right - rect.left;
                    int h = rect.bottom - rect.top;
                    if (w < 220 || h < 140) return true;

                    IntByReference pidRef = new IntByReference();
                    u.GetWindowThreadProcessId(hwnd, pidRef);
                    long pid = Integer.toUnsignedLong(pidRef.getValue());
                    if (pid == ownPid) return true;

                    String rawApp = processName(pid);
                    String rawAppLower = rawApp.toLowerCase(Locale.ROOT);
                    String titleLower = title.toLowerCase(Locale.ROOT);
                    if (BLOCKED_APPS.contains(rawAppLower) || BLOCKED_TITLES.contains(titleLower)) return true;
                    if (titleLower.equals("settings") && rawAppLower.contains("applicationframehost")) return true;

                    long exStyle = Integer.toUnsignedLong(u.GetWindowLong(hwnd, GWL_EXSTYLE));
                    if ((exStyle & WS_EX_TOOLWINDOW) != 0L) return true;

                    String app = prettyApp(rawApp);
                    long handle = Pointer.nativeValue(hwnd.getPointer());
                    String key = handle + "|" + app + "|" + title;
                    if (!seen.add(key)) return true;

                    out.add(new WindowInfo(handle, app, title, new Rectangle(rect.left, rect.top, w, h)));
                } catch (Throwable ignored) {}
                return true;
            }, null);
        } catch (Throwable ignored) {
            return List.of();
        }

        out.sort(Comparator.comparing(WindowInfo::app, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WindowInfo::title, String.CASE_INSENSITIVE_ORDER));
        return out;
    }


    /** Fast GDI copy path. Much cheaper than PrintWindow and suitable for high-rate live windows. */
    public static BufferedImage captureWindowFast(long rawHwnd) {
        WinDef.HWND target = hwnd(rawHwnd);
        if (target == null || !User32Extra.INSTANCE.IsWindow(target)) return null;
        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(target, rect)) return null;
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;
        if (width < 2 || height < 2 || width > 16384 || height > 16384) return null;

        WinDef.HDC windowDc = User32Extra.INSTANCE.GetWindowDC(target);
        if (windowDc == null) return null;
        WinDef.HDC memoryDc = null;
        WinDef.HBITMAP bitmap = null;
        WinNT.HANDLE original = null;
        try {
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
            if (memoryDc == null) return null;
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, width, height);
            if (bitmap == null) return null;
            original = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (original == null) return null;
            final int SRCCOPY = 0x00CC0020;
            final int CAPTUREBLT = 0x40000000;
            if (!GDI32.INSTANCE.BitBlt(memoryDc, 0, 0, width, height, windowDc, 0, 0, SRCCOPY | CAPTUREBLT)) return null;

            WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
            bmi.bmiHeader.biWidth = width;
            bmi.bmiHeader.biHeight = -height;
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
            Memory buffer = new Memory((long) width * height * 4L);
            int rows = GDI32.INSTANCE.GetDIBits(memoryDc, bitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS);
            if (rows == 0) return null;
            int count = width * height;
            int[] pixels = buffer.getIntArray(0, count);
            DataBufferInt data = new DataBufferInt(pixels, count);
            WritableRaster raster = Raster.createPackedRaster(data, width, height, width, BAND_MASKS, null);
            return new BufferedImage(COLOR_MODEL, raster, false, null);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (original != null && memoryDc != null) GDI32.INSTANCE.SelectObject(memoryDc, original); } catch (Throwable ignored) {}
            try { if (bitmap != null) GDI32.INSTANCE.DeleteObject(bitmap); } catch (Throwable ignored) {}
            try { if (memoryDc != null) GDI32.INSTANCE.DeleteDC(memoryDc); } catch (Throwable ignored) {}
            try { User32Extra.INSTANCE.ReleaseDC(target, windowDc); } catch (Throwable ignored) {}
        }
    }

    /**
     * Captures the complete top-level window (including custom title/tab bars) without moving, focusing or resizing it.
     * PrintWindow/PW_RENDERFULLCONTENT is preferred because plain BitBlt often misses modern non-client/compositor content.
     */
    public static BufferedImage captureWindow(long rawHwnd) {
        WinDef.HWND target = hwnd(rawHwnd);
        if (target == null || !User32Extra.INSTANCE.IsWindow(target)) return null;

        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(target, rect)) return null;
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;
        if (width < 2 || height < 2 || width > 16384 || height > 16384) return null;

        WinDef.HDC windowDc = User32Extra.INSTANCE.GetDC(target);
        if (windowDc == null) return null;
        WinDef.HDC memoryDc = null;
        WinDef.HBITMAP bitmap = null;
        WinNT.HANDLE original = null;
        try {
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(windowDc);
            if (memoryDc == null) return null;
            bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(windowDc, width, height);
            if (bitmap == null) return null;
            original = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (original == null) return null;

            boolean painted = User32Extra.INSTANCE.PrintWindow(target, memoryDc, PW_RENDERFULLCONTENT);
            if (!painted) painted = User32Extra.INSTANCE.PrintWindow(target, memoryDc, 0);
            if (!painted) return null;

            WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
            bmi.bmiHeader.biWidth = width;
            bmi.bmiHeader.biHeight = -height;
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
            Memory buffer = new Memory((long) width * height * 4L);
            int rows = GDI32.INSTANCE.GetDIBits(memoryDc, bitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS);
            if (rows == 0) return null;

            int count = width * height;
            int[] pixels = buffer.getIntArray(0, count);
            DataBufferInt data = new DataBufferInt(pixels, count);
            WritableRaster raster = Raster.createPackedRaster(data, width, height, width, BAND_MASKS, null);
            return new BufferedImage(COLOR_MODEL, raster, false, null);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (original != null && memoryDc != null) GDI32.INSTANCE.SelectObject(memoryDc, original); } catch (Throwable ignored) {}
            try { if (bitmap != null) GDI32.INSTANCE.DeleteObject(bitmap); } catch (Throwable ignored) {}
            try { if (memoryDc != null) GDI32.INSTANCE.DeleteDC(memoryDc); } catch (Throwable ignored) {}
            try { User32Extra.INSTANCE.ReleaseDC(target, windowDc); } catch (Throwable ignored) {}
        }
    }



    /** Rejects the all-black/empty DC frames modern DWM windows can report as successful captures. */
    public static boolean isUsefulFrame(BufferedImage image) {
        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) return false;
        int w = image.getWidth(), h = image.getHeight();
        int sx = Math.max(1, w / 24), sy = Math.max(1, h / 16);
        int samples = 0, nonBlack = 0;
        int minL = 255, maxL = 0;
        for (int y = sy / 2; y < h; y += sy) {
            for (int x = sx / 2; x < w; x += sx) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >>> 16) & 255, g = (rgb >>> 8) & 255, b = rgb & 255;
                int l = (r * 54 + g * 183 + b * 19) >>> 8;
                if (l > 5) nonBlack++;
                if (l < minL) minL = l;
                if (l > maxL) maxL = l;
                samples++;
            }
        }
        if (samples == 0) return false;
        // Dark-themed apps are valid; reject only near-uniform black surfaces.
        return nonBlack >= Math.max(3, samples / 80) || (maxL - minL) >= 10;
    }


    /** Very cheap sampled signature used only to detect a stale fast-capture surface. */
    public static long frameSignature(BufferedImage image) {
        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) return 0L;
        int w = image.getWidth(), h = image.getHeight();
        int sx = Math.max(1, w / 13), sy = Math.max(1, h / 9);
        long hash = 0xcbf29ce484222325L;
        for (int y = sy / 2; y < h; y += sy) {
            for (int x = sx / 2; x < w; x += sx) {
                hash ^= Integer.toUnsignedLong(image.getRGB(x, y));
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }


    /** Raw top-down NativeImage-ready ABGR frame. */
    public record RawFrame(int width, int height, int[] pixelsAbgr, long signature) {}

    /**
     * Persistent GDI capture resources for one HWND. CreateCompatibleDC/Bitmap/Memory are kept alive
     * across frames instead of being rebuilt 30-120 times per second. The instance is thread-confined
     * to one SpatialMonitor capture worker.
     */
    public static final class CaptureSession implements AutoCloseable {
        private final long rawHwnd;
        private WinDef.HDC memoryDc;
        private WinDef.HBITMAP bitmap;
        private WinNT.HANDLE original;
        private Memory buffer;
        private WinGDI.BITMAPINFO bmi;
        private int width;
        private int height;
        private boolean closed;

        public CaptureSession(long rawHwnd) {
            this.rawHwnd = rawHwnd;
        }

        public RawFrame captureFast(int[] reuse) {
            if (!ensureResources()) return null;
            WinDef.HWND target = hwnd(rawHwnd);
            if (target == null) return null;
            WinDef.HDC windowDc = User32Extra.INSTANCE.GetWindowDC(target);
            if (windowDc == null) return null;
            try {
                final int SRCCOPY = 0x00CC0020;
                final int CAPTUREBLT = 0x40000000;
                if (!GDI32.INSTANCE.BitBlt(memoryDc, 0, 0, width, height, windowDc, 0, 0, SRCCOPY | CAPTUREBLT)) return null;
            } catch (Throwable ignored) {
                return null;
            } finally {
                try { User32Extra.INSTANCE.ReleaseDC(target, windowDc); } catch (Throwable ignored) {}
            }
            return readFrame(reuse);
        }

        public RawFrame capturePrint(int[] reuse) {
            if (!ensureResources()) return null;
            WinDef.HWND target = hwnd(rawHwnd);
            if (target == null) return null;
            try {
                boolean painted = User32Extra.INSTANCE.PrintWindow(target, memoryDc, PW_RENDERFULLCONTENT);
                if (!painted) painted = User32Extra.INSTANCE.PrintWindow(target, memoryDc, 0);
                if (!painted) return null;
            } catch (Throwable ignored) {
                return null;
            }
            return readFrame(reuse);
        }

        private boolean ensureResources() {
            if (closed) return false;
            WinDef.HWND target = hwnd(rawHwnd);
            if (target == null || !User32Extra.INSTANCE.IsWindow(target)) return false;
            WinDef.RECT rect = new WinDef.RECT();
            if (!User32.INSTANCE.GetWindowRect(target, rect)) return false;
            int w = rect.right - rect.left;
            int h = rect.bottom - rect.top;
            if (w < 2 || h < 2 || w > 4096 || h > 4096) return false;
            if (memoryDc != null && bitmap != null && buffer != null && w == width && h == height) return true;

            releaseResources();
            WinDef.HDC referenceDc = User32Extra.INSTANCE.GetWindowDC(target);
            if (referenceDc == null) return false;
            try {
                memoryDc = GDI32.INSTANCE.CreateCompatibleDC(referenceDc);
                if (memoryDc == null) { releaseResources(); return false; }
                bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(referenceDc, w, h);
                if (bitmap == null) { releaseResources(); return false; }
                original = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
                if (original == null) { releaseResources(); return false; }
                width = w;
                height = h;
                buffer = new Memory((long)w * h * 4L);
                bmi = new WinGDI.BITMAPINFO();
                bmi.bmiHeader.biWidth = w;
                bmi.bmiHeader.biHeight = -h;
                bmi.bmiHeader.biPlanes = 1;
                bmi.bmiHeader.biBitCount = 32;
                bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
                return true;
            } catch (Throwable ignored) {
                releaseResources();
                return false;
            } finally {
                try { User32Extra.INSTANCE.ReleaseDC(target, referenceDc); } catch (Throwable ignored) {}
            }
        }

        private RawFrame readFrame(int[] reuse) {
            try {
                int rows = GDI32.INSTANCE.GetDIBits(memoryDc, bitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS);
                if (rows == 0) return null;
                int count = width * height;
                int[] pixels = reuse != null && reuse.length >= count ? reuse : new int[count];
                buffer.read(0L, pixels, 0, count);
                // DIB bytes are B,G,R,0. NativeImage's 32-bit backing store expects ABGR.
                for (int i = 0; i < count; i++) {
                    int c = pixels[i];
                    int r = (c >>> 16) & 0xFF;
                    int g = (c >>> 8) & 0xFF;
                    int b = c & 0xFF;
                    pixels[i] = 0xFF000000 | (b << 16) | (g << 8) | r;
                }
                long sig = frameSignatureAbgr(pixels, width, height);
                return isUsefulFrameAbgr(pixels, width, height) ? new RawFrame(width, height, pixels, sig) : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private void releaseResources() {
            try { if (original != null && memoryDc != null) GDI32.INSTANCE.SelectObject(memoryDc, original); } catch (Throwable ignored) {}
            try { if (bitmap != null) GDI32.INSTANCE.DeleteObject(bitmap); } catch (Throwable ignored) {}
            try { if (memoryDc != null) GDI32.INSTANCE.DeleteDC(memoryDc); } catch (Throwable ignored) {}
            original = null;
            bitmap = null;
            memoryDc = null;
            buffer = null;
            bmi = null;
            width = 0;
            height = 0;
        }

        @Override
        public void close() {
            closed = true;
            releaseResources();
        }
    }

    public static boolean isUsefulFrameAbgr(int[] pixels, int width, int height) {
        if (pixels == null || width < 2 || height < 2 || pixels.length < width * height) return false;
        int sx = Math.max(1, width / 24), sy = Math.max(1, height / 16);
        int samples = 0, nonBlack = 0, minL = 255, maxL = 0;
        for (int y = sy / 2; y < height; y += sy) {
            int row = y * width;
            for (int x = sx / 2; x < width; x += sx) {
                int c = pixels[row + x];
                int r = c & 255, g = (c >>> 8) & 255, b = (c >>> 16) & 255;
                int l = (r * 54 + g * 183 + b * 19) >>> 8;
                if (l > 5) nonBlack++;
                if (l < minL) minL = l;
                if (l > maxL) maxL = l;
                samples++;
            }
        }
        return samples > 0 && (nonBlack >= Math.max(3, samples / 80) || (maxL - minL) >= 10);
    }

    public static long frameSignatureAbgr(int[] pixels, int width, int height) {
        if (pixels == null || width < 2 || height < 2 || pixels.length < width * height) return 0L;
        int sx = Math.max(1, width / 13), sy = Math.max(1, height / 9);
        long hash = 0xcbf29ce484222325L;
        for (int y = sy / 2; y < height; y += sy) {
            int row = y * width;
            for (int x = sx / 2; x < width; x += sx) {
                hash ^= Integer.toUnsignedLong(pixels[row + x]);
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    public static boolean isForeground(long rawHwnd) {
        try {
            WinDef.HWND target = hwnd(rawHwnd);
            WinDef.HWND fg = User32.INSTANCE.GetForegroundWindow();
            if (target == null || fg == null || target.getPointer() == null || fg.getPointer() == null) return false;
            return Pointer.nativeValue(target.getPointer()) == Pointer.nativeValue(fg.getPointer());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Fallback for hardware-accelerated fullscreen Chromium video. PrintWindow can return
     * a permanently stale frame for DirectComposition surfaces. We only read the desktop
     * when the target itself is the foreground window, so this never Alt+Tabs/focuses apps
     * and never captures Minecraft over a background browser window.
     */
    public static BufferedImage captureForegroundCompositedWindow(long rawHwnd) {
        try {
            WinDef.HWND target = hwnd(rawHwnd);
            if (target == null || !User32Extra.INSTANCE.IsWindow(target)) return null;
            WinDef.HWND fg = User32.INSTANCE.GetForegroundWindow();
            if (fg == null || fg.getPointer() == null || target.getPointer() == null) return null;
            if (Pointer.nativeValue(fg.getPointer()) != Pointer.nativeValue(target.getPointer())) return null;

            Rectangle r = getBounds(rawHwnd);
            if (r == null || r.width < 2 || r.height < 2) return null;
            Rectangle desktop = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            Rectangle safe = r.intersection(desktop);
            if (safe.width < 2 || safe.height < 2) return null;
            return new Robot().createScreenCapture(safe);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Maps a point from the captured full-window image to the deepest visible Win32 child under it. */
    public static InputTarget inputTarget(long rawHwnd, double u, double v) {
        try {
            WinDef.HWND top = hwnd(rawHwnd);
            if (top == null || !User32Extra.INSTANCE.IsWindow(top)) return null;
            WinDef.RECT wr = new WinDef.RECT();
            if (!User32.INSTANCE.GetWindowRect(top, wr)) return null;
            int width = Math.max(1, wr.right - wr.left);
            int height = Math.max(1, wr.bottom - wr.top);
            int sx = wr.left + (int)Math.round(clamp(u) * (width - 1));
            int sy = wr.top + (int)Math.round(clamp(v) * (height - 1));

            WinDef.POINT p = new WinDef.POINT();
            p.x = sx;
            p.y = sy;
            if (!User32Extra.INSTANCE.ScreenToClient(top, p)) return null;

            WinDef.HWND current = top;
            int cx = p.x;
            int cy = p.y;
            for (int depth = 0; depth < 10; depth++) {
                WinDef.POINT local = new WinDef.POINT();
                local.x = cx;
                local.y = cy;
                WinDef.HWND child = User32Extra.INSTANCE.ChildWindowFromPointEx(current, local,
                        CWP_SKIPINVISIBLE | CWP_SKIPDISABLED | CWP_SKIPTRANSPARENT);
                if (child == null || child.getPointer() == null || child.equals(current)) break;

                WinDef.POINT childPoint = new WinDef.POINT();
                childPoint.x = sx;
                childPoint.y = sy;
                if (!User32Extra.INSTANCE.ScreenToClient(child, childPoint)) break;
                current = child;
                cx = childPoint.x;
                cy = childPoint.y;
            }
            return new InputTarget(Pointer.nativeValue(current.getPointer()), cx, cy, sx, sy);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Rectangle getBounds(long rawHwnd) {
        try {
            WinDef.HWND hwnd = hwnd(rawHwnd);
            WinDef.RECT r = new WinDef.RECT();
            if (!User32.INSTANCE.GetWindowRect(hwnd, r)) return null;
            int w = r.right - r.left;
            int h = r.bottom - r.top;
            return w > 1 && h > 1 ? new Rectangle(r.left, r.top, w, h) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isAlive(long rawHwnd) {
        try { return User32Extra.INSTANCE.IsWindow(hwnd(rawHwnd)); }
        catch (Throwable ignored) { return false; }
    }

    public static WinDef.HWND hwnd(long raw) {
        return new WinDef.HWND(Pointer.createConstant(raw));
    }

    private static String processName(long pid) {
        try {
            return ProcessHandle.of(pid)
                    .flatMap(ph -> ph.info().command())
                    .map(cmd -> {
                        try { return Path.of(cmd).getFileName().toString().replaceFirst("(?i)\\.exe$", ""); }
                        catch (Throwable ignored) { return cmd; }
                    })
                    .orElse("App");
        } catch (Throwable ignored) {
            return "App";
        }
    }

    private static String prettyApp(String s) {
        if (s == null || s.isBlank()) return "App";
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("discord")) return "Discord";
        if (low.contains("telegram")) return "Telegram";
        if (low.equals("chrome")) return "Chrome";
        if (low.equals("msedge")) return "Edge";
        if (low.equals("firefox")) return "Firefox";
        if (low.equals("explorer")) return "Explorer";
        if (low.equals("spotify")) return "Spotify";
        if (low.equals("code")) return "VS Code";
        if (low.equals("idea64") || low.equals("idea")) return "IntelliJ IDEA";
        if (low.equals("opera") || low.equals("opera_gx")) return "Opera";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replace('|', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
}
