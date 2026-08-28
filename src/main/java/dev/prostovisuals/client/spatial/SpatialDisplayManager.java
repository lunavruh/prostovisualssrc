package dev.prostovisuals.client.spatial;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpatialDisplayManager {
    private static final SpatialDisplayManager INSTANCE = new SpatialDisplayManager();
    private final List<SpatialMonitor> monitors = new ArrayList<>();
    private int nextId = 1;

    private SpatialMonitor hoveredMonitor;
    private SpatialMonitor focusedMonitor;
    private SpatialMonitor.Hit hoveredHit;
    private long lastPointerPostAt;
    private double lastPointerU = -1.0;
    private double lastPointerV = -1.0;

    private SpatialDisplayManager() {}
    public static SpatialDisplayManager getInstance() { return INSTANCE; }
    public List<SpatialMonitor> getMonitors() { return Collections.unmodifiableList(monitors); }

    public SpatialMonitor addMonitor(CaptureSource source, float distance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        int slot = monitors.size();
        int side = slot == 0 ? 0 : ((slot & 1) == 1 ? 1 : -1);
        int ring = slot == 0 ? 0 : (slot + 1) / 2;
        double sideOffset = side * ring * 2.25;
        float arcYaw = side * ring * 13.0f;
        double baseYaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        Vec3d right = new Vec3d(Math.cos(baseYaw), 0.0, Math.sin(baseYaw));
        Vec3d center = cam.add(look.multiply(distance)).add(right.multiply(sideOffset));
        float yaw = mc.gameRenderer.getCamera().getYaw() + arcYaw;
        SpatialMonitor m = new SpatialMonitor(nextId++, center, yaw, source);
        monitors.add(m);
        return m;
    }

    public void recenter(int index, float distance) {
        if (index < 0 || index >= monitors.size()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        SpatialMonitor monitor = monitors.get(index);
        monitor.center = cam.add(look.multiply(distance));
        monitor.yawDegrees = mc.gameRenderer.getCamera().getYaw();
    }

    public void remove(int index) {
        if (index < 0 || index >= monitors.size()) return;
        SpatialMonitor m = monitors.remove(index);
        if (m == hoveredMonitor) { hoveredMonitor = null; hoveredHit = null; }
        if (m == focusedMonitor) releaseFocus();
        m.close();
    }

    public void clear() {
        releaseFocus();
        for (SpatialMonitor m : monitors) m.close();
        monitors.clear();
        hoveredMonitor = null;
        hoveredHit = null;
    }

    public void tickCapture(int fps) {
        long now = System.currentTimeMillis();
        int targetFps = Math.max(30, Math.min(120, fps));
        long interval = Math.max(8L, 1000L / targetFps);
        // Every spatial window stays live at the same cadence. Never throttle or stop a second
        // monitor merely because keyboard/crosshair focus moved to another one.
        for (SpatialMonitor monitor : monitors) {
            monitor.requestCapture(now, interval);
        }
    }

    public void render(MatrixStack matrices, float alpha) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Pause/settings screens render the world first and then their own blurred GUI. Preserve the
        // exact GL toggles/blend factors around Spatial Display only in that path so the following
        // GUI cannot inherit our depth/blend state. No glGet* overhead is paid during normal gameplay.
        RenderStateSnapshot pauseState = mc.currentScreen != null ? RenderStateSnapshot.capture() : null;
        try {
            boolean guiOpen = mc.currentScreen != null;
            for (SpatialMonitor monitor : monitors) {
                // Texture uploads touch GPU texture state. While Esc/settings GUI is compositing its
                // blur, keep rendering the last completed texture but defer uploads until gameplay.
                if (!guiOpen) monitor.uploadPendingFrame();
                monitor.render(matrices, alpha);
            }
        } finally {
            if (pauseState != null) pauseState.restore();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        if (mc.currentScreen == null) updateCrosshairTarget();
        else { hoveredMonitor = null; hoveredHit = null; }
    }

    private record RenderStateSnapshot(boolean blend, boolean cull, boolean depth, boolean depthMask,
                                       int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        static RenderStateSnapshot capture() {
            return new RenderStateSnapshot(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
            );
        }

        void restore() {
            if (blend) com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            else com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            if (cull) com.mojang.blaze3d.systems.RenderSystem.enableCull();
            else com.mojang.blaze3d.systems.RenderSystem.disableCull();
            if (depth) com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            else com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(depthMask);
            com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    /** Minecraft's crosshair is the only pointer; no extra cursor is rendered. */
    public void updateCrosshairTarget() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || monitors.isEmpty()) {
            hoveredMonitor = null;
            hoveredHit = null;
            return;
        }

        Vec3d origin = mc.gameRenderer.getCamera().getPos();
        Vec3d direction = mc.player.getRotationVec(1.0f).normalize();
        SpatialMonitor bestMonitor = null;
        SpatialMonitor.Hit bestHit = null;
        for (SpatialMonitor monitor : monitors) {
            SpatialMonitor.Hit hit = monitor.raycast(origin, direction);
            if (hit != null && (bestHit == null || hit.distance() < bestHit.distance())) {
                bestMonitor = monitor;
                bestHit = hit;
            }
        }
        hoveredMonitor = bestMonitor;
        hoveredHit = bestHit;

        if (bestMonitor != null && bestHit != null && bestMonitor.getSource() instanceof WindowCaptureSource window) {
            long now = System.nanoTime();
            boolean moved = Math.abs(bestHit.u() - lastPointerU) > 0.0008 || Math.abs(bestHit.v() - lastPointerV) > 0.0008;
            if (moved && now - lastPointerPostAt >= 14_000_000L) {
                SpatialInputBridge.move(window, bestHit.u(), bestHit.v());
                lastPointerPostAt = now;
                lastPointerU = bestHit.u();
                lastPointerV = bestHit.v();
            }
        }
    }

    public boolean handleMouseButton(int button, int action) {
        updateCrosshairTarget();
        if (hoveredMonitor == null || hoveredHit == null) return false;
        if (!(hoveredMonitor.getSource() instanceof WindowCaptureSource window)) return false;
        if (action == GLFW.GLFW_PRESS) focusedMonitor = hoveredMonitor;
        boolean handled = SpatialInputBridge.mouseButton(window, hoveredHit.u(), hoveredHit.v(), button, action);
        return handled;
    }

    public boolean handleScroll(double vertical) {
        updateCrosshairTarget();
        if (hoveredMonitor == null || hoveredHit == null) return false;
        if (!(hoveredMonitor.getSource() instanceof WindowCaptureSource window)) return false;
        // Scrolling a panel is interaction too: immediately promote that exact monitor to the
        // high-rate capture path, independent of whichever panel had keyboard focus before.
        focusedMonitor = hoveredMonitor;
        boolean handled = SpatialInputBridge.scroll(window, hoveredHit.u(), hoveredHit.v(), vertical);
        return handled;
    }

    public boolean handleKey(int key, int action, int modifiers) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || focusedMonitor == null) return false;
        if (!(focusedMonitor.getSource() instanceof WindowCaptureSource window)) return false;

        // Esc releases spatial keyboard focus but is NOT swallowed: the same press opens Minecraft's pause menu normally.
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
            releaseFocus();
            return false;
        }

        boolean ctrlAltSuper = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean printable = isPrintableKey(key);

        // Plain printable text is consumed here but emitted only by onChar/WM_CHAR.
        // This prevents the previous double-input/RU-layout corruption.
        if (printable && !ctrlAltSuper) return true;

        boolean posted = SpatialInputBridge.key(window, key, action);
        // Even if a target app ignores the key, focused mode must keep Minecraft from acting on it.
        return posted || isControlKey(key) || ctrlAltSuper;
    }

    public boolean handleCharacter(int codePoint) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || focusedMonitor == null) return false;
        if (!(focusedMonitor.getSource() instanceof WindowCaptureSource window)) return false;
        SpatialInputBridge.character(window, codePoint);
        return true;
    }

    public void releaseFocus() {
        if (focusedMonitor != null && focusedMonitor.getSource() instanceof WindowCaptureSource window) {
            SpatialInputBridge.release(window);
        }
        focusedMonitor = null;
    }

    public boolean hasTextFocus() { return focusedMonitor != null; }
    public boolean isCrosshairOverMonitor() { return hoveredMonitor != null && hoveredHit != null; }

    private static boolean isPrintableKey(int key) {
        return (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
                || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
                || key == GLFW.GLFW_KEY_SPACE
                || (key >= GLFW.GLFW_KEY_APOSTROPHE && key <= GLFW.GLFW_KEY_GRAVE_ACCENT)
                || (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_EQUAL);
    }

    private static boolean isControlKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB
                || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_INSERT
                || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END || key == GLFW.GLFW_KEY_PAGE_UP
                || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_LEFT_SHIFT
                || key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_LEFT_CONTROL
                || key == GLFW.GLFW_KEY_RIGHT_CONTROL || key == GLFW.GLFW_KEY_LEFT_ALT
                || key == GLFW.GLFW_KEY_RIGHT_ALT || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }
}
