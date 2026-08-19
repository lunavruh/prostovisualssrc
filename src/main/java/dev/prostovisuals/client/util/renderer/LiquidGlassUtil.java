package dev.prostovisuals.client.util.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Shared Liquid Glass renderer.
 *
 * The visual model is adapted from the supplied Rockstar liquid-glass assets:
 * rounded SDF + edge Fresnel + refraction.  The expensive radial blur from the
 * reference pack is deliberately not copied.  The whole scene is captured once
 * per frame at half resolution and the fragment shader uses a compact 9-tap
 * filter.  Every HUD/GUI glass panel reuses that same capture.
 */
public final class LiquidGlassUtil {
    private static final float CAPTURE_SCALE = 0.50f;
    private static final int GL_LINEAR = 9729;

    private static final ShaderProgramKey LIQUID_GLASS_SHADER =
            new ShaderProgramKey(
                    Identifier.of("prostovisuals", "core/liquid_glass"),
                    VertexFormats.POSITION_COLOR,
                    Defines.EMPTY
            );

    private static SimpleFramebuffer backgroundFramebuffer;
    private static int backgroundWidth = -1;
    private static int backgroundHeight = -1;
    private static long lastCapturedFrame = Long.MIN_VALUE;

    private static final Vector4f TMP_TOP_LEFT = new Vector4f();
    private static final Vector4f TMP_BOTTOM_RIGHT = new Vector4f();

    private static ShaderProgram uniformShader;
    private static GlUniform sizeUniform;
    private static GlUniform radiusUniform;
    private static GlUniform smoothnessUniform;
    private static GlUniform screenSizeUniform;
    private static GlUniform panelCenterUniform;
    private static GlUniform timeUniform;
    private static GlUniform blurRadiusUniform;
    private static GlUniform distortionSpeedUniform;
    private static GlUniform distortionIntensityUniform;
    private static GlUniform rimStrengthUniform;
    private static GlUniform zoomUniform;

    private LiquidGlassUtil() {}

    /** Capture the undimmed scene once. Call before drawing a menu backdrop. */
    public static void captureFrame() {
        RenderSystem.assertOnRenderThread();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0) return;

        int captureW = Math.max(2, Math.round(main.textureWidth * CAPTURE_SCALE));
        int captureH = Math.max(2, Math.round(main.textureHeight * CAPTURE_SCALE));
        ensureBackgroundFramebuffer(captureW, captureH);

        long frame = client.getRenderTime();
        if (lastCapturedFrame == frame) return;

        backgroundFramebuffer.beginWrite(false);
        main.draw(backgroundFramebuffer.textureWidth, backgroundFramebuffer.textureHeight);
        main.beginWrite(false);
        lastCapturedFrame = frame;
    }

    /** Shared capture for the legacy blur builder; avoids another full-screen copy. */
    public static int getCapturedTexture() {
        captureFrame();
        return backgroundFramebuffer == null ? 0 : backgroundFramebuffer.getColorAttachment();
    }

    public static int getCapturedWidth() {
        return backgroundFramebuffer == null ? 1 : Math.max(1, backgroundFramebuffer.textureWidth);
    }

    public static int getCapturedHeight() {
        return backgroundFramebuffer == null ? 1 : Math.max(1, backgroundFramebuffer.textureHeight);
    }

    public static void drawLiquidGlass(DrawContext context, float x, float y, float width, float height, float time) {
        drawLiquidGlass(context, x, y, width, height, time,
                4.2f, 0.24f, 0.0018f,
                Math.min(height * 0.5f, 12.0f), 0.58f, 1.032f);
    }

    public static void drawLiquidGlass(
            DrawContext context, float x, float y, float width, float height, float time,
            float blurRadius, float distortionSpeed, float distortionIntensity,
            float cornerRadius, float rimStrength
    ) {
        drawLiquidGlass(context, x, y, width, height, time,
                blurRadius, distortionSpeed, distortionIntensity, cornerRadius, rimStrength, 1.032f);
    }

    public static void drawLiquidGlass(
            DrawContext context, float x, float y, float width, float height, float time,
            float blurRadius, float distortionSpeed, float distortionIntensity,
            float cornerRadius, float rimStrength, float zoom
    ) {
        if (context == null || width <= 0.0f || height <= 0.0f) return;
        RenderSystem.assertOnRenderThread();

        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0) return;

        captureFrame();
        if (backgroundFramebuffer == null) return;

        int guiWidth = Math.max(1, context.getScaledWindowWidth());
        int guiHeight = Math.max(1, context.getScaledWindowHeight());
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        Vector4f topLeft = TMP_TOP_LEFT.set(x, y, 0.0f, 1.0f).mul(matrix);
        Vector4f bottomRight = TMP_BOTTOM_RIGHT.set(x + width, y + height, 0.0f, 1.0f).mul(matrix);
        float actualX = Math.min(topLeft.x, bottomRight.x);
        float actualY = Math.min(topLeft.y, bottomRight.y);
        float actualWidth = Math.max(0.01f, Math.abs(bottomRight.x - topLeft.x));
        float actualHeight = Math.max(0.01f, Math.abs(bottomRight.y - topLeft.y));
        float matrixScale = Math.max(0.01f, (actualWidth / width + actualHeight / height) * 0.5f);

        float centerU = (actualX + actualWidth * 0.5f) / guiWidth;
        float centerV = 1.0f - ((actualY + actualHeight * 0.5f) / guiHeight);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, backgroundFramebuffer.getColorAttachment());

        ShaderProgram shader = RenderSystem.setShader(LIQUID_GLASS_SHADER);
        if (shader == null) {
            restoreRenderState();
            return;
        }

        cacheUniforms(shader);
        if (sizeUniform != null) sizeUniform.set(actualWidth, actualHeight);
        float scaledRadius = Math.max(0.0f, cornerRadius * matrixScale);
        if (radiusUniform != null) radiusUniform.set(scaledRadius, scaledRadius, scaledRadius, scaledRadius);
        if (smoothnessUniform != null) smoothnessUniform.set(1.0f);
        if (screenSizeUniform != null) screenSizeUniform.set(
                (float) backgroundFramebuffer.textureWidth,
                (float) backgroundFramebuffer.textureHeight
        );
        if (panelCenterUniform != null) panelCenterUniform.set(centerU, centerV);
        if (timeUniform != null) timeUniform.set(time);
        if (blurRadiusUniform != null) blurRadiusUniform.set(Math.max(0.0f, Math.min(10.0f, blurRadius)));
        if (distortionSpeedUniform != null) distortionSpeedUniform.set(Math.max(0.0f, distortionSpeed));
        if (distortionIntensityUniform != null) distortionIntensityUniform.set(Math.max(0.0f, distortionIntensity));
        if (rimStrengthUniform != null) rimStrengthUniform.set(Math.max(0.0f, Math.min(1.5f, rimStrength)));
        if (zoomUniform != null) zoomUniform.set(Math.max(1.0f, Math.min(1.12f, zoom)));

        BufferBuilder builder = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR
        );
        builder.vertex(matrix, x, y, 0.0f).color(0xFFFFFFFF);
        builder.vertex(matrix, x, y + height, 0.0f).color(0xFFFFFFFF);
        builder.vertex(matrix, x + width, y + height, 0.0f).color(0xFFFFFFFF);
        builder.vertex(matrix, x + width, y, 0.0f).color(0xFFFFFFFF);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        restoreRenderState();
    }

    private static void ensureBackgroundFramebuffer(int width, int height) {
        if (backgroundFramebuffer == null) {
            backgroundFramebuffer = new SimpleFramebuffer(width, height, false);
            backgroundFramebuffer.setTexFilter(GL_LINEAR);
            backgroundWidth = width;
            backgroundHeight = height;
            lastCapturedFrame = Long.MIN_VALUE;
            return;
        }

        if (backgroundWidth != width || backgroundHeight != height) {
            backgroundFramebuffer.resize(width, height);
            backgroundFramebuffer.setTexFilter(GL_LINEAR);
            backgroundWidth = width;
            backgroundHeight = height;
            lastCapturedFrame = Long.MIN_VALUE;
        }
    }

    private static void restoreRenderState() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void cacheUniforms(ShaderProgram shader) {
        if (uniformShader == shader) return;
        uniformShader = shader;
        sizeUniform = shader.getUniform("Size");
        radiusUniform = shader.getUniform("Radius");
        smoothnessUniform = shader.getUniform("Smoothness");
        screenSizeUniform = shader.getUniform("ScreenSize");
        panelCenterUniform = shader.getUniform("PanelCenter");
        timeUniform = shader.getUniform("Time");
        blurRadiusUniform = shader.getUniform("BlurRadius");
        distortionSpeedUniform = shader.getUniform("DistortionSpeed");
        distortionIntensityUniform = shader.getUniform("DistortionIntensity");
        rimStrengthUniform = shader.getUniform("RimStrength");
        zoomUniform = shader.getUniform("Zoom");
    }

    public static void close() {
        if (backgroundFramebuffer != null) {
            backgroundFramebuffer.delete();
            backgroundFramebuffer = null;
        }
        backgroundWidth = -1;
        backgroundHeight = -1;
        lastCapturedFrame = Long.MIN_VALUE;
        uniformShader = null;
        sizeUniform = radiusUniform = smoothnessUniform = null;
        screenSizeUniform = panelCenterUniform = null;
        timeUniform = blurRadiusUniform = distortionSpeedUniform = null;
        distortionIntensityUniform = rimStrengthUniform = zoomUniform = null;
    }
}
