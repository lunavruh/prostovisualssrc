package dev.prostovisuals.client.render.renderers.impl;

import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.prostovisuals.client.render.builders.states.QuadColorState;
import dev.prostovisuals.client.render.builders.states.QuadRadiusState;
import dev.prostovisuals.client.render.builders.states.SizeState;
import dev.prostovisuals.client.render.providers.ResourceProvider;
import dev.prostovisuals.client.render.renderers.IRenderer;

public record BuiltBorder(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float thickness,
        float internalSmoothness, float externalSmoothness
    ) implements IRenderer {

    private static final ShaderProgramKey RECTANGLE_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("border"), VertexFormats.POSITION_COLOR, Defines.EMPTY);
    private static ShaderProgram cachedShader;
    private static GlUniform sizeUniform, radiusUniform, thicknessUniform, smoothnessUniform;

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        renderFast(matrix, x, y, z, this.size.width(), this.size.height(),
                this.radius.radius1(), this.radius.radius2(), this.radius.radius3(), this.radius.radius4(),
                this.color.color1(), this.color.color2(), this.color.color3(), this.color.color4(),
                this.thickness, this.internalSmoothness, this.externalSmoothness);
    }

    public static void renderFast(Matrix4f matrix, float x, float y, float z,
                                  float width, float height,
                                  float radius1, float radius2, float radius3, float radius4,
                                  int color1, int color2, int color3, int color4,
                                  float thickness, float internalSmoothness, float externalSmoothness) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        ShaderProgram shader = RenderSystem.setShader(RECTANGLE_SHADER_KEY);
        cacheUniforms(shader);
        sizeUniform.set(width, height);
        radiusUniform.set(radius1, radius2, radius3, radius4);
        thicknessUniform.set(thickness);
        smoothnessUniform.set(internalSmoothness, externalSmoothness);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(color1);
        builder.vertex(matrix, x, y + height, z).color(color2);
        builder.vertex(matrix, x + width, y + height, z).color(color3);
        builder.vertex(matrix, x + width, y, z).color(color4);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void cacheUniforms(ShaderProgram shader) {
        if (cachedShader == shader) return;
        cachedShader = shader;
        sizeUniform = shader.getUniform("Size");
        radiusUniform = shader.getUniform("Radius");
        thicknessUniform = shader.getUniform("Thickness");
        smoothnessUniform = shader.getUniform("Smoothness");
    }
}
