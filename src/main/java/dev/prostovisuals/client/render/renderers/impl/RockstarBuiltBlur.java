package dev.prostovisuals.client.render.renderers.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.render.builders.states.QuadColorState;
import dev.prostovisuals.client.render.builders.states.QuadRadiusState;
import dev.prostovisuals.client.render.builders.states.SizeState;
import dev.prostovisuals.client.render.providers.ResourceProvider;
import dev.prostovisuals.client.render.renderers.IRenderer;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Lightweight legacy blur builder.
 * Reuses LiquidGlassUtil's one-per-frame half-resolution scene capture instead
 * of allocating/copying a full-size framebuffer for every blurred rectangle.
 */
public record RockstarBuiltBlur(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float blurRadius
) implements IRenderer {

    private static final ShaderProgramKey BLUR_SHADER_KEY = new ShaderProgramKey(
            ResourceProvider.getShaderIdentifier("rock_blur"),
            VertexFormats.POSITION_COLOR,
            Defines.EMPTY
    );

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        int texture = LiquidGlassUtil.getCapturedTexture();
        if (texture == 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, texture);

        float width = size.width();
        float height = size.height();
        ShaderProgram shader = RenderSystem.setShader(BLUR_SHADER_KEY);
        if (shader == null) {
            restore();
            return;
        }

        set2(shader.getUniform("Size"), width, height);
        GlUniform radiusUniform = shader.getUniform("Radius");
        if (radiusUniform != null) {
            radiusUniform.set(radius.radius1(), radius.radius2(), radius.radius3(), radius.radius4());
        }
        set1(shader.getUniform("Smoothness"), smoothness);
        set1(shader.getUniform("BlurRadius"), Math.max(0.0f, Math.min(10.0f, blurRadius)));

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(color.color1());
        builder.vertex(matrix, x, y + height, z).color(color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(color.color3());
        builder.vertex(matrix, x + width, y, z).color(color.color4());
        BufferRenderer.drawWithGlobalProgram(builder.end());
        restore();
    }

    private static void set1(GlUniform uniform, float value) {
        if (uniform != null) uniform.set(value);
    }

    private static void set2(GlUniform uniform, float x, float y) {
        if (uniform != null) uniform.set(x, y);
    }

    private static void restore() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
