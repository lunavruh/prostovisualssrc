package dev.prostovisuals.client.spatial;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.prostovisuals.mixin.accessors.INativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SpatialMonitor implements AutoCloseable {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final int ACTIVE_CAPTURE_WIDTH = 1440;
        private static final double FRONT_SURFACE_OFFSET = -0.055;
    private static final double REAR_CASE_OFFSET = 0.045;

    private final int index;
    private record PendingFrame(long generation, int width, int height, int[] pixelsAbgr, float aspect) {}

    private final AtomicReference<PendingFrame> pendingFrame = new AtomicReference<>();
    private final AtomicReference<int[]> freePixelBuffer = new AtomicReference<>();
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile CaptureSource source;
    private volatile long sourceGeneration = 1L;
    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private volatile long lastCaptureAt;
    private volatile long lastCaptureRequestAt;
    private volatile long requestedIntervalMs = 16L;
    private volatile int requestedCaptureWidth = ACTIVE_CAPTURE_WIDTH;
    private final Thread captureWorker;
    private volatile float sourceAspect = 16f / 9f;

    public Vec3d center;
    public float yawDegrees;
    public float width = 2.20f;
    public float height = 1.24f;
    public float curvature = 0.0f;
    public int segments = 4;
    public boolean locked = true;

    public SpatialMonitor(int index, Vec3d center, float yawDegrees, CaptureSource source) {
        this.index = index;
        this.center = center;
        this.yawDegrees = yawDegrees;
        this.source = source;
        this.captureWorker = Thread.ofPlatform().daemon(true).name("ProstoSpatial-Capture-" + index).start(this::captureLoop);
    }

    public record Hit(double u, double v, double distance) {}

    /** Intersects only the FRONT of the curved panel; the rear is intentionally non-interactive. */
    public Hit raycast(Vec3d origin, Vec3d direction) {
        if (origin == null || direction == null || center == null) return null;
        Vec3d dir = direction.normalize();
        double yaw = Math.toRadians(yawDegrees);
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d up = new Vec3d(0, 1, 0);

        // The screen front points toward -forward (where it is created from the player's camera).
        if (origin.subtract(center).dotProduct(forward) >= 0.0) return null;

        Vec3d rel = origin.subtract(center);
        double px = rel.dotProduct(right);
        double py = rel.dotProduct(up);
        double pz = rel.dotProduct(forward);
        double dx = dir.dotProduct(right);
        double dy = dir.dotProduct(up);
        double dz = dir.dotProduct(forward);

        double halfW = Math.max(0.001, width * 0.5);
        double halfH = Math.max(0.001, height * 0.5);
        double k = curvature / (halfW * halfW);
        double a = k * dx * dx;
        double b = 2.0 * k * px * dx - dz;
        double c = k * px * px - pz;

        double best = Double.POSITIVE_INFINITY;
        if (Math.abs(a) < 1.0e-8) {
            if (Math.abs(b) > 1.0e-8) {
                double t = -c / b;
                if (t > 0.02) best = t;
            }
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc >= 0.0) {
                double root = Math.sqrt(disc);
                double t0 = (-b - root) / (2.0 * a);
                double t1 = (-b + root) / (2.0 * a);
                if (t0 > 0.02) best = t0;
                if (t1 > 0.02 && t1 < best) best = t1;
            }
        }
        if (!Double.isFinite(best)) return null;

        double x = px + dx * best;
        double y = py + dy * best;
        if (x < -halfW || x > halfW || y < -halfH || y > halfH) return null;

        double u = 1.0 - ((x + halfW) / (2.0 * halfW));
        double v = 1.0 - ((y + halfH) / (2.0 * halfH));
        return new Hit(clamp(u), clamp(v), best);
    }

    public String getName() { return source == null ? "No source" : source.name(); }
    public CaptureSource getSource() { return source; }

    public void setSource(CaptureSource next) {
        CaptureSource old = this.source;
        this.source = next;
        if (old != null && old != next) try { old.close(); } catch (Exception ignored) {}
        sourceGeneration++;
        PendingFrame oldPending = pendingFrame.getAndSet(null);
        if (oldPending != null) recyclePixels(oldPending.pixelsAbgr());
    }

    /** Cheap culling so off-screen/back-facing monitors do not keep capturing/uploading frames. */
    public boolean isLikelyVisible() {
        if (center == null || MC.player == null || MC.currentScreen != null) return false;
        Vec3d camera = MC.gameRenderer.getCamera().getPos();
        Vec3d to = center.subtract(camera);
        double dist = to.length();
        if (dist < 0.05 || dist > 28.0) return false;
        double yaw = Math.toRadians(yawDegrees);
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        if (camera.subtract(center).dotProduct(forward) >= 0.0) return false;
        // Do not gate capture by a narrow crosshair cone. With two or more Vision panels a side
        // monitor can be fully visible while its centre is far from the crosshair; the old >0.20
        // test made that panel freeze on its last frame. Front-facing + distance is enough here.
        Vec3d look = MC.player.getRotationVec(1.0f).normalize();
        return look.dotProduct(to.multiply(1.0 / dist)) > -0.45;
    }

    /**
     * Render code only publishes the desired rate. A single persistent worker owns capture for this
     * monitor, so there is never one virtual thread per frame and never a queue of stale frames.
     */
    public void requestCapture(long now, long intervalMs) {
        requestedIntervalMs = Math.max(8L, Math.min(250L, intervalMs));
        requestedCaptureWidth = ACTIVE_CAPTURE_WIDTH;
        lastCaptureRequestAt = now;
    }

    private void captureLoop() {
        long nextFrameAt = 0L;
        while (!closed.get()) {
            try {
                long now = System.currentTimeMillis();
                // Rendering/culling publishes requests continuously. If it stops, capture sleeps too.
                if (now - lastCaptureRequestAt > 220L || source == null || !source.isAvailable()) {
                    Thread.sleep(35L);
                    nextFrameAt = 0L;
                    continue;
                }
                long interval = requestedIntervalMs;
                if (nextFrameAt > now) {
                    Thread.sleep(Math.min(8L, Math.max(1L, nextFrameAt - now)));
                    continue;
                }
                if (!captureInFlight.compareAndSet(false, true)) {
                    Thread.sleep(1L);
                    continue;
                }
                long started = System.currentTimeMillis();
                try {
                    CaptureSource current = source;
                    long generation = sourceGeneration;
                    if (current == null || !current.isAvailable()) continue;
                    int w;
                    int h;
                    float aspect;
                    int[] pixels;

                    if (current instanceof WindowCaptureSource windowSource) {
                        // Native hot path: persistent GDI resources -> ABGR int[] -> NativeImage.
                        // No BufferedImage allocation, Java2D scaling or getRGB conversion per frame.
                        int[] reuse = freePixelBuffer.getAndSet(null);
                        WindowNative.RawFrame raw = windowSource.captureRaw(reuse);
                        if (raw == null) {
                            recyclePixels(reuse);
                            continue;
                        }
                        pixels = raw.pixelsAbgr();
                        w = raw.width();
                        h = raw.height();
                        aspect = w / (float)Math.max(1, h);
                    } else {
                        BufferedImage image = current.capture();
                        if (image == null || image.getWidth() < 2 || image.getHeight() < 2) continue;
                        BufferedImage scaled = scale(image, requestedCaptureWidth);
                        w = scaled.getWidth();
                        h = scaled.getHeight();
                        aspect = w / (float)Math.max(1, h);
                        pixels = acquirePixelBuffer(w * h);
                        fillAbgr(scaled, pixels);
                    }
                    // A source may be changed while its old capture is still in progress. Never let
                    // that stale frame overwrite the new monitor/source texture.
                    if (generation != sourceGeneration || current != source) {
                        recyclePixels(pixels);
                        continue;
                    }
                    PendingFrame replaced = pendingFrame.getAndSet(new PendingFrame(generation, w, h, pixels, aspect));
                    if (replaced != null) recyclePixels(replaced.pixelsAbgr()); // drop old frame; never queue it
                    lastCaptureAt = System.currentTimeMillis();
                } catch (Throwable ignored) {
                } finally {
                    captureInFlight.set(false);
                }
                long spent = Math.max(0L, System.currentTimeMillis() - started);
                // If the backend is slower than target FPS, run at backend speed rather than piling work up.
                nextFrameAt = System.currentTimeMillis() + Math.max(0L, interval - spent);
            } catch (InterruptedException ignored) {
                if (closed.get()) break;
            } catch (Throwable ignored) {
                try { Thread.sleep(25L); } catch (InterruptedException ignored2) { if (closed.get()) break; }
            }
        }
    }


    /**
     * Input never forces native capture. Capture and input are intentionally independent so Chromium/
     * Electron do not get hammered by synchronous PrintWindow calls after every character/click.
     */
    public void requestImmediateRefresh() {
        // No-op by design. The persistent worker picks up the next frame at its normal cadence.
    }

    /** Render-thread work is one bulk native-memory copy + GPU upload; no per-pixel Java calls. */
    public void uploadPendingFrame() {
        PendingFrame frame = pendingFrame.getAndSet(null);
        if (frame == null) return;
        int[] pixels = frame.pixelsAbgr();
        if (frame.generation() != sourceGeneration) {
            recyclePixels(pixels);
            return;
        }
        try {
            sourceAspect = frame.aspect();
            height = Math.max(1.05f, Math.min(2.05f, width / Math.max(0.90f, Math.min(2.45f, sourceAspect))));

            NativeImage image = texture == null ? null : texture.getImage();
            if (image == null || image.getWidth() != frame.width() || image.getHeight() != frame.height()) {
                NativeImage replacement = new NativeImage(frame.width(), frame.height(), false);
                if (texture == null) {
                    texture = new NativeImageBackedTexture(replacement);
                    textureId = Identifier.of("prostovisuals", "spatial_" + index);
                    MC.getTextureManager().registerTexture(textureId, texture);
                } else {
                    texture.setImage(replacement);
                }
                texture.setFilter(true, false);
                image = replacement;
            }

            long pointer = ((INativeImage)(Object)image).prostovisuals$getPointer();
            if (pointer == 0L) throw new IllegalStateException("NativeImage pointer is null");
            var dst = MemoryUtil.memIntBuffer(pointer, frame.width() * frame.height());
            dst.position(0);
            dst.put(pixels, 0, frame.width() * frame.height());
            dst.position(0);
            texture.upload();
        } catch (Throwable ignored) {
        } finally {
            recyclePixels(pixels);
        }
    }

    public void render(MatrixStack matrices, float alpha) {
        if (center == null) return;
        Vec3d camera = MC.gameRenderer.getCamera().getPos();
        double yaw = Math.toRadians(yawDegrees);
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        boolean front = camera.subtract(center).dotProduct(forward) < 0.0;

        // Case is visible from both sides; the LCD texture is front-only.
        renderCase(matrices, camera, Math.max(0.35f, alpha));
        if (!front || textureId == null) return;

        try {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, textureId);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            emitTexturedSurface(buffer, matrix, camera, width, height, FRONT_SURFACE_OFFSET, alpha);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            // Restore only state modified by this draw. Do not force a global blend mode here;
            // the custom HUD pass owns its own blend state and was turning black after Esc when
            // Spatial Display overrode it at the end of the world pass.
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    /** Thin dark bezel + rear shell. No captured image is ever rendered on the rear. */
    private void renderCase(MatrixStack matrices, Vec3d camera, float alpha) {
        try {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            emitSolidSurface(buffer, matrix, camera, width + 0.060f, height + 0.060f, REAR_CASE_OFFSET, 0.028f, 0.031f, 0.038f, Math.min(1f, alpha));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private void emitTexturedSurface(BufferBuilder buffer, Matrix4f matrix, Vec3d camera,
                                     float panelW, float panelH, double zOffset, float alpha) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d up = new Vec3d(0, 1, 0);
        double halfW = panelW * 0.5;
        double halfH = panelH * 0.5;
        int seg = Math.max(4, Math.min(48, segments));

        for (int i = 0; i < seg; i++) {
            double u0 = i / (double) seg;
            double u1 = (i + 1) / (double) seg;
            double x0 = -halfW + panelW * u0;
            double x1 = -halfW + panelW * u1;
            double z0 = curvature * Math.pow(x0 / Math.max(0.001, halfW), 2.0) + zOffset;
            double z1 = curvature * Math.pow(x1 / Math.max(0.001, halfW), 2.0) + zOffset;

            Vec3d bl = center.add(right.multiply(x0)).add(forward.multiply(z0)).subtract(up.multiply(halfH)).subtract(camera);
            Vec3d br = center.add(right.multiply(x1)).add(forward.multiply(z1)).subtract(up.multiply(halfH)).subtract(camera);
            Vec3d tr = center.add(right.multiply(x1)).add(forward.multiply(z1)).add(up.multiply(halfH)).subtract(camera);
            Vec3d tl = center.add(right.multiply(x0)).add(forward.multiply(z0)).add(up.multiply(halfH)).subtract(camera);

            buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).texture((float)(1.0 - u0), 1f).color(1f,1f,1f,alpha);
            buffer.vertex(matrix, (float)br.x, (float)br.y, (float)br.z).texture((float)(1.0 - u1), 1f).color(1f,1f,1f,alpha);
            buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).texture((float)(1.0 - u1), 0f).color(1f,1f,1f,alpha);
            buffer.vertex(matrix, (float)tl.x, (float)tl.y, (float)tl.z).texture((float)(1.0 - u0), 0f).color(1f,1f,1f,alpha);
        }
    }

    private void emitSolidSurface(BufferBuilder buffer, Matrix4f matrix, Vec3d camera,
                                  float panelW, float panelH, double zOffset,
                                  float r, float g, float b, float a) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d up = new Vec3d(0, 1, 0);
        double halfW = panelW * 0.5;
        double halfH = panelH * 0.5;
        int seg = Math.max(4, Math.min(48, segments));
        for (int i = 0; i < seg; i++) {
            double f0 = i / (double)seg;
            double f1 = (i + 1) / (double)seg;
            double x0 = -halfW + panelW * f0;
            double x1 = -halfW + panelW * f1;
            double z0 = curvature * Math.pow(x0 / Math.max(0.001, halfW), 2.0) + zOffset;
            double z1 = curvature * Math.pow(x1 / Math.max(0.001, halfW), 2.0) + zOffset;
            Vec3d bl = center.add(right.multiply(x0)).add(forward.multiply(z0)).subtract(up.multiply(halfH)).subtract(camera);
            Vec3d br = center.add(right.multiply(x1)).add(forward.multiply(z1)).subtract(up.multiply(halfH)).subtract(camera);
            Vec3d tr = center.add(right.multiply(x1)).add(forward.multiply(z1)).add(up.multiply(halfH)).subtract(camera);
            Vec3d tl = center.add(right.multiply(x0)).add(forward.multiply(z0)).add(up.multiply(halfH)).subtract(camera);
            buffer.vertex(matrix, (float)bl.x, (float)bl.y, (float)bl.z).color(r,g,b,a);
            buffer.vertex(matrix, (float)br.x, (float)br.y, (float)br.z).color(r,g,b,a);
            buffer.vertex(matrix, (float)tr.x, (float)tr.y, (float)tr.z).color(r,g,b,a);
            buffer.vertex(matrix, (float)tl.x, (float)tl.y, (float)tl.z).color(r,g,b,a);
        }
    }

    private int[] acquirePixelBuffer(int count) {
        int[] pixels = freePixelBuffer.getAndSet(null);
        if (pixels == null || pixels.length != count) pixels = new int[count];
        return pixels;
    }

    private void recyclePixels(int[] pixels) {
        if (pixels == null || closed.get()) return;
        freePixelBuffer.compareAndSet(null, pixels);
    }

    /** BufferedImage bulk read + in-place ARGB -> NativeImage ABGR conversion. */
    private static void fillAbgr(BufferedImage image, int[] pixels) {
        int w = image.getWidth();
        int h = image.getHeight();
        image.getRGB(0, 0, w, h, pixels, 0, w);
        int count = w * h;
        int i = 0;
        // Unrolled loop keeps the capture worker CPU-bound for much less time than setColorArgb/getRGB per pixel.
        for (; i + 3 < count; i += 4) {
            pixels[i] = argbToAbgr(pixels[i]);
            pixels[i + 1] = argbToAbgr(pixels[i + 1]);
            pixels[i + 2] = argbToAbgr(pixels[i + 2]);
            pixels[i + 3] = argbToAbgr(pixels[i + 3]);
        }
        for (; i < count; i++) pixels[i] = argbToAbgr(pixels[i]);
    }

    private static int argbToAbgr(int c) {
        return (c & 0xFF00FF00) | ((c >>> 16) & 0xFF) | ((c & 0xFF) << 16);
    }

    private static BufferedImage scale(BufferedImage src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return src;
        int w = maxWidth;
        int h = Math.max(1, (int)Math.round(src.getHeight() * (maxWidth / (double)src.getWidth())));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    @Override public void close() {
        closed.set(true);
        try { captureWorker.interrupt(); } catch (Throwable ignored) {}
        try { if (source != null) source.close(); } catch (Exception ignored) {}
        PendingFrame pending = pendingFrame.getAndSet(null);
        if (pending != null) recyclePixels(pending.pixelsAbgr());
        if (textureId != null) MC.getTextureManager().destroyTexture(textureId);
        else if (texture != null) texture.close();
        texture = null;
        textureId = null;
    }
}
