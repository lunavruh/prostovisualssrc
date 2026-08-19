package dev.prostovisuals.client.spatial;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends background input to the real Win32 child under the spatial crosshair.
 * The real Windows cursor is never moved or drawn.
 */
public final class SpatialInputBridge {
    private static final int WM_MOUSEMOVE = 0x0200;
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_RBUTTONDOWN = 0x0204;
    private static final int WM_RBUTTONUP = 0x0205;
    private static final int WM_MBUTTONDOWN = 0x0207;
    private static final int WM_MBUTTONUP = 0x0208;
    private static final int WM_MOUSEWHEEL = 0x020A;
    private static final int WM_VSCROLL = 0x0115;
    private static final int SB_LINEUP = 0;
    private static final int SB_LINEDOWN = 1;
    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_KEYUP = 0x0101;
    private static final int WM_CHAR = 0x0102;
    private static final int MK_LBUTTON = 0x0001;
    private static final int MK_RBUTTON = 0x0002;
    private static final int MK_MBUTTON = 0x0010;

    /** top-level HWND -> last child/control clicked inside that app */
    private static final ConcurrentHashMap<Long, Long> FOCUS_TARGET = new ConcurrentHashMap<>();

    private SpatialInputBridge() {}

    public static boolean move(WindowCaptureSource source, double u, double v) {
        WindowNative.InputTarget p = WindowNative.inputTarget(source.hwnd(), u, v);
        if (p == null) return false;
        return post(p.hwnd(), WM_MOUSEMOVE, 0, pack(p.clientX(), p.clientY()));
    }

    public static boolean mouseButton(WindowCaptureSource source, double u, double v, int button, int action) {
        WindowNative.InputTarget p = WindowNative.inputTarget(source.hwnd(), u, v);
        if (p == null) return false;
        int msg;
        int mask;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            msg = action == GLFW.GLFW_PRESS ? WM_LBUTTONDOWN : WM_LBUTTONUP;
            mask = MK_LBUTTON;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            msg = action == GLFW.GLFW_PRESS ? WM_RBUTTONDOWN : WM_RBUTTONUP;
            mask = MK_RBUTTON;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            msg = action == GLFW.GLFW_PRESS ? WM_MBUTTONDOWN : WM_MBUTTONUP;
            mask = MK_MBUTTON;
        } else return false;

        if (action == GLFW.GLFW_PRESS) {
            // Remember the control for keyboard routing, but never synthesize WM_SETFOCUS.
            // Chromium/Electron can treat background focus churn as a real focus transition and
            // repeatedly rebuild/reload parts of the UI.
            FOCUS_TARGET.put(source.hwnd(), p.hwnd());
        }
        return post(p.hwnd(), msg, action == GLFW.GLFW_PRESS ? mask : 0, pack(p.clientX(), p.clientY()));
    }

    public static boolean scroll(WindowCaptureSource source, double u, double v, double vertical) {
        WindowNative.InputTarget p = WindowNative.inputTarget(source.hwnd(), u, v);
        if (p == null) return false;
        int delta = (int)Math.round(vertical * 120.0);
        if (delta == 0) delta = vertical > 0 ? 120 : -120;
        long wParam = ((long)(delta & 0xFFFF)) << 16;
        long lParam = pack(p.screenX(), p.screenY());

        // Move the logical pointer first; Chromium/Electron use this for wheel hit-testing.
        post(source.hwnd(), WM_MOUSEMOVE, 0, pack(p.clientX(), p.clientY()));
        if (p.hwnd() != source.hwnd()) post(p.hwnd(), WM_MOUSEMOVE, 0, pack(p.clientX(), p.clientY()));

        // Send synchronously first. Chromium/Electron can discard a queued background wheel event,
        // while SendMessage reaches the target window procedure immediately. Keep PostMessage as fallback.
        boolean top = send(source.hwnd(), WM_MOUSEWHEEL, wParam, lParam) || post(source.hwnd(), WM_MOUSEWHEEL, wParam, lParam);
        boolean child = p.hwnd() == source.hwnd() || send(p.hwnd(), WM_MOUSEWHEEL, wParam, lParam) || post(p.hwnd(), WM_MOUSEWHEEL, wParam, lParam);

        // Native edit/list controls often use WM_VSCROLL rather than wheel. Chromium/Electron normally
        // consume WM_MOUSEWHEEL above, while this fallback makes Explorer and classic Win32 panes scroll.
        int scrollCode = delta > 0 ? SB_LINEUP : SB_LINEDOWN;
        int lines = Math.max(1, Math.min(6, Math.abs(delta) / 120 * 3));
        boolean vscroll = false;
        for (int i = 0; i < lines; i++) {
            vscroll |= post(p.hwnd(), WM_VSCROLL, scrollCode, 0);
            if (p.hwnd() != source.hwnd()) vscroll |= post(source.hwnd(), WM_VSCROLL, scrollCode, 0);
        }
        return top || child || vscroll;
    }

    public static boolean key(WindowCaptureSource source, int glfwKey, int action) {
        int vk = toVirtualKey(glfwKey);
        if (vk == 0) return false;
        long target = keyboardTarget(source);
        int msg = action == GLFW.GLFW_RELEASE ? WM_KEYUP : WM_KEYDOWN;
        return post(target, msg, vk, 0);
    }

    /** Unicode text path. Printable keys are sent only here, never duplicated as WM_KEYDOWN text. */
    public static boolean character(WindowCaptureSource source, int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) return false;
        long target = keyboardTarget(source);
        if (codePoint <= 0xFFFF) return post(target, WM_CHAR, codePoint, 0);
        char[] pair = Character.toChars(codePoint);
        boolean a = post(target, WM_CHAR, pair[0], 0);
        boolean b = post(target, WM_CHAR, pair[1], 0);
        return a || b;
    }

    public static void release(WindowCaptureSource source) {
        if (source != null) FOCUS_TARGET.remove(source.hwnd());
    }

    private static long keyboardTarget(WindowCaptureSource source) {
        return FOCUS_TARGET.getOrDefault(source.hwnd(), source.hwnd());
    }

    private static boolean send(long hwnd, int msg, long wParam, long lParam) {
        try {
            User32.INSTANCE.SendMessage(WindowNative.hwnd(hwnd), msg,
                    new WinDef.WPARAM(wParam), new WinDef.LPARAM(lParam));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean post(long hwnd, int msg, long wParam, long lParam) {
        try {
            User32.INSTANCE.PostMessage(WindowNative.hwnd(hwnd), msg,
                    new WinDef.WPARAM(wParam), new WinDef.LPARAM(lParam));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long pack(int x, int y) {
        return ((long)(y & 0xFFFF) << 16) | (x & 0xFFFFL);
    }

    private static int toVirtualKey(int k) {
        if ((k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) || (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9)) return k;
        return switch (k) {
            case GLFW.GLFW_KEY_SPACE -> 0x20;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 0x0D;
            case GLFW.GLFW_KEY_TAB -> 0x09;
            case GLFW.GLFW_KEY_BACKSPACE -> 0x08;
            case GLFW.GLFW_KEY_DELETE -> 0x2E;
            case GLFW.GLFW_KEY_INSERT -> 0x2D;
            case GLFW.GLFW_KEY_HOME -> 0x24;
            case GLFW.GLFW_KEY_END -> 0x23;
            case GLFW.GLFW_KEY_PAGE_UP -> 0x21;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 0x22;
            case GLFW.GLFW_KEY_LEFT -> 0x25;
            case GLFW.GLFW_KEY_UP -> 0x26;
            case GLFW.GLFW_KEY_RIGHT -> 0x27;
            case GLFW.GLFW_KEY_DOWN -> 0x28;
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> 0x10;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> 0x11;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> 0x12;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> 0x5B;
            case GLFW.GLFW_KEY_ESCAPE -> 0x1B;
            case GLFW.GLFW_KEY_F1 -> 0x70;
            case GLFW.GLFW_KEY_F2 -> 0x71;
            case GLFW.GLFW_KEY_F3 -> 0x72;
            case GLFW.GLFW_KEY_F4 -> 0x73;
            case GLFW.GLFW_KEY_F5 -> 0x74;
            case GLFW.GLFW_KEY_F6 -> 0x75;
            case GLFW.GLFW_KEY_F7 -> 0x76;
            case GLFW.GLFW_KEY_F8 -> 0x77;
            case GLFW.GLFW_KEY_F9 -> 0x78;
            case GLFW.GLFW_KEY_F10 -> 0x79;
            case GLFW.GLFW_KEY_F11 -> 0x7A;
            case GLFW.GLFW_KEY_F12 -> 0x7B;
            default -> 0;
        };
    }
}
