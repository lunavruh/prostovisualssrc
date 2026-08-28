package dev.prostovisuals.client.render.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.modules.impl.render.MotionBlur;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Lightweight camera motion blur.
 *
 * Performance notes:
 *  - the source frame is copied to a half-resolution framebuffer;
 *  - only 3/5/7 texture samples are used instead of 10-28;
 *  - sample count goes DOWN at high FPS instead of up;
 *  - tiny camera movement skips the post-process entirely.
 *
 * This keeps the visible camera smear while avoiding the huge GPU bandwidth
 * cost of the old full-resolution 20+ sample pass.
 */
public final class MotionBlurRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final ShaderProgramKey SHADER = new ShaderProgramKey(
            Identifier.of("prostovisuals", "core/motion_blur"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );

    // 0.5x in both dimensions = 1/4 of the pixels copied every frame.
    private static final float COPY_SCALE = 0.50f;

    private static SimpleFramebuffer sceneCopy;
    private static int copyWidth = -1;
    private static int copyHeight = -1;

    private static float previousYaw;
    private static float previousPitch;
    private static boolean havePreviousCamera;

    private static long lastRenderNanos;
    private static float currentFps = 60.0f;

    private static ShaderProgram cachedShader;
    private static GlUniform viewResolutionUniform;
    private static GlUniform motionPixelsUniform;
    private static GlUniform strengthUniform;
    private static GlUniform sampleCountUniform;

    private MotionBlurRenderer() {}

    public static void render() {
        RenderSystem.assertOnRenderThread();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getCamera();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        if (!havePreviousCamera) {
            previousYaw = yaw;
            previousPitch = pitch;
            havePreviousCamera = true;
            return;
        }

        float yawDelta = wrapDegrees(yaw - previousYaw);
        float pitchDelta = pitch - previousPitch;
        previousYaw = yaw;
        previousPitch = pitch;

        MotionBlur module = prostovisuals.getInstance().getModuleManager().getModule(MotionBlur.class);
        if (client.world == null || client.player == null || module == null || !module.isToggled()) return;
        if (client.currentScreen != null) return;

        float strength = clamp(module.getStrength() / 100.0f, 0.0f, 1.0f);
        if (strength <= 0.001f) return;

        long now = System.nanoTime();
        if (lastRenderNanos != 0L) {
            float dt = (now - lastRenderNanos) / 1_000_000_000.0f;
            if (dt > 0.0001f && dt < 1.0f) currentFps = 1.0f / dt;
        }
        lastRenderNanos = now;

        Framebuffer main = client.getFramebuffer();
        if (main == null || main.viewportWidth <= 0 || main.viewportHeight <= 0) return;

        // Ignore camera mode swaps / discontinuities.
        if (Math.abs(yawDelta) > 70.0f || Math.abs(pitchDelta) > 70.0f) return;

        // Preserve the visual strength of the previous version, but don't make
        // the shader more expensive just because FPS is high.
        float fpsScale = clamp(currentFps / 60.0f, 1.0f, 2.15f);
        float pixelsPerDegree = (3.0f + 7.25f * strength) * fpsScale;
        float motionX = -yawDelta * pixelsPerDegree;
        float motionY = pitchDelta * pixelsPerDegree;

        float length = (float) Math.sqrt(motionX * motionX + motionY * motionY);

        // Tiny sub-pixel movement is invisible as blur, so don't pay for a
        // framebuffer copy + fullscreen pass in that case.
        if (length < 0.65f) return;

        float maxPixels = 16.0f + 70.0f * strength;
        if (length > maxPixels) {
            float scale = maxPixels / length;
            motionX *= scale;
            motionY *= scale;
            length = maxPixels;
        }

        int wantedWidth = Math.max(1, Math.round(main.textureWidth * COPY_SCALE));
        int wantedHeight = Math.max(1, Math.round(main.textureHeight * COPY_SCALE));
        ensureSceneCopy(wantedWidth, wantedHeight);
        if (sceneCopy == null) return;

        copyColor(main, sceneCopy);
        int samples = chooseSampleCount(currentFps, strength, length);
        drawBlur(main, motionX, motionY, strength, samples);
    }

    private static void drawBlur(Framebuffer main, float motionX, float motionY, float strength, int samples) {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.setShaderTexture(0, sceneCopy.getColorAttachment());

        ShaderProgram shader = RenderSystem.setShader(SHADER);
        if (shader == null) {
            restoreState();
            return;
        }

        cacheUniforms(shader);
        if (viewResolutionUniform != null) viewResolutionUniform.set((float) main.viewportWidth, (float) main.viewportHeight);
        if (motionPixelsUniform != null) motionPixelsUniform.set(motionX, motionY);
        if (strengthUniform != null) strengthUniform.set(strength);
        if (sampleCountUniform != null) sampleCountUniform.set(samples);

        BufferBuilder builder = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );
        builder.vertex(IDENTITY, -1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f).color(0xFFFFFFFF);
        builder.vertex(IDENTITY,  1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f).color(0xFFFFFFFF);
        builder.vertex(IDENTITY,  1.0f,  1.0f, 0.0f).texture(1.0f, 1.0f).color(0xFFFFFFFF);
        builder.vertex(IDENTITY, -1.0f,  1.0f, 0.0f).texture(0.0f, 1.0f).color(0xFFFFFFFF);

        BufferRenderer.drawWithGlobalProgram(builder.end());
        restoreState();
    }

    private static void copyColor(Framebuffer main, Framebuffer copy) {
        // main.draw scales directly into the smaller FBO on the GPU. This cuts
        // copy bandwidth by ~75% compared to the old full-resolution copy.
        copy.beginWrite(false);
        main.draw(copy.textureWidth, copy.textureHeight);
        main.beginWrite(false);
    }

    private static void ensureSceneCopy(int width, int height) {
        if (sceneCopy == null) {
            sceneCopy = new SimpleFramebuffer(width, height, false);
            sceneCopy.setTexFilter(9729); // GL_LINEAR; important when upsampling.
            copyWidth = width;
            copyHeight = height;
            return;
        }

        if (copyWidth != width || copyHeight != height) {
            sceneCopy.resize(width, height);
            sceneCopy.setTexFilter(9729);
            copyWidth = width;
            copyHeight = height;
        }
    }

    /**
     * Keep the pass cheap: 3 taps for small movement / struggling FPS,
     * 5 normally, and only 7 for a strong fast sweep.
     */
    private static int chooseSampleCount(float fps, float strength, float motionLength) {
        if (fps < 90.0f) return 3;
        if (motionLength < 8.0f || strength < 0.28f) return 3;
        if (fps > 155.0f) return 5;
        if (motionLength > 30.0f && strength > 0.70f) return 7;
        return 5;
    }

    private static void cacheUniforms(ShaderProgram shader) {
        if (cachedShader == shader) return;
        cachedShader = shader;
        viewResolutionUniform = shader.getUniform("ViewResolution");
        motionPixelsUniform = shader.getUniform("MotionPixels");
        strengthUniform = shader.getUniform("Strength");
        sampleCountUniform = shader.getUniform("SampleCount");
    }

    private static void restoreState() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static void reset() {
        previousYaw = previousPitch = 0.0f;
        havePreviousCamera = false;
        lastRenderNanos = 0L;
        currentFps = 60.0f;
    }

    public static void close() {
        if (sceneCopy != null) {
            sceneCopy.delete();
            sceneCopy = null;
        }
        copyWidth = copyHeight = -1;
        cachedShader = null;
        viewResolutionUniform = motionPixelsUniform = strengthUniform = sampleCountUniform = null;
        reset();
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
