package dev.prostovisuals.client.render.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.modules.impl.render.CustomFog;
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
 * Safe single-pass atmospheric fog blur inspired by Kimiko's fog blur.
 * It intentionally does not replace Minecraft's framebuffer/depth pipeline:
 * one 0.65x color copy + five taps, applied mostly around the distant horizon.
 */
public final class FogBlurRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final ShaderProgramKey SHADER = new ShaderProgramKey(
            Identifier.of("prostovisuals", "core/fog_blur"),
            VertexFormats.POSITION_TEXTURE_COLOR,
            Defines.EMPTY
    );
    private static final float COPY_SCALE = 0.65f;
    private static SimpleFramebuffer sceneCopy;
    private static int copyW = -1, copyH = -1;
    private static ShaderProgram cachedShader;
    private static GlUniform resolutionUniform, strengthUniform, horizonUniform;

    private FogBlurRenderer() {}

    public static void render() {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.gameRenderer == null) return;
        // Do not touch pause/click GUI compositing; this is the safety rule that avoids black-screen regressions.
        if (client.currentScreen != null) return;

        CustomFog fog = prostovisuals.getInstance().getModuleManager().getModule(CustomFog.class);
        if (fog == null || !fog.isToggled() || !fog.isFogBlurEnabled()) return;
        float strength = Math.max(0.0f, Math.min(0.80f, fog.getFogBlurStrength()));
        if (strength < 0.01f) return;

        Framebuffer main = client.getFramebuffer();
        if (main == null || main.textureWidth <= 0 || main.textureHeight <= 0) return;
        int w = Math.max(1, Math.round(main.textureWidth * COPY_SCALE));
        int h = Math.max(1, Math.round(main.textureHeight * COPY_SCALE));
        ensureCopy(w, h);
        sceneCopy.beginWrite(false);
        main.draw(sceneCopy.textureWidth, sceneCopy.textureHeight);
        main.beginWrite(false);

        Camera camera = client.gameRenderer.getCamera();
        float pitch = camera == null ? 0.0f : camera.getPitch();
        float horizon = 0.50f + Math.max(-0.22f, Math.min(0.22f, pitch / 180.0f));

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, sceneCopy.getColorAttachment());
        ShaderProgram shader = RenderSystem.setShader(SHADER);
        if (shader == null) { restore(); return; }
        cache(shader);
        if (resolutionUniform != null) resolutionUniform.set((float) sceneCopy.textureWidth, (float) sceneCopy.textureHeight);
        if (strengthUniform != null) strengthUniform.set(strength);
        if (horizonUniform != null) horizonUniform.set(horizon);

        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(IDENTITY,-1,-1,0).texture(0,0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, 1,-1,0).texture(1,0).color(0xFFFFFFFF);
        b.vertex(IDENTITY, 1, 1,0).texture(1,1).color(0xFFFFFFFF);
        b.vertex(IDENTITY,-1, 1,0).texture(0,1).color(0xFFFFFFFF);
        BufferRenderer.drawWithGlobalProgram(b.end());
        restore();
    }

    private static void ensureCopy(int w, int h) {
        if (sceneCopy == null) {
            sceneCopy = new SimpleFramebuffer(w, h, false);
            sceneCopy.setTexFilter(9729);
            copyW = w; copyH = h;
        } else if (copyW != w || copyH != h) {
            sceneCopy.resize(w, h);
            sceneCopy.setTexFilter(9729);
            copyW = w; copyH = h;
        }
    }

    private static void cache(ShaderProgram shader) {
        if (cachedShader == shader) return;
        cachedShader = shader;
        resolutionUniform = shader.getUniform("ViewResolution");
        strengthUniform = shader.getUniform("Strength");
        horizonUniform = shader.getUniform("Horizon");
    }

    private static void restore() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
