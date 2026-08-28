package dev.prostovisuals.client.render.renderers.impl;

import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.prostovisuals.client.render.builders.states.QuadColorState;
import dev.prostovisuals.client.render.builders.states.QuadRadiusState;
import dev.prostovisuals.client.render.builders.states.SizeState;
import dev.prostovisuals.client.render.providers.ResourceProvider;
import dev.prostovisuals.client.render.renderers.IRenderer;

public record BuiltTexture(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float u, float v,
        float texWidth, float texHeight,
        int textureId
    ) implements IRenderer {

    private static final ShaderProgramKey TEXTURE_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("texture"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private static ShaderProgram cachedShader;
    private static GlUniform sizeUniform, radiusUniform, smoothnessUniform;
    
    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        renderFast(matrix, x, y, z, this.size.width(), this.size.height(),
                this.radius.radius1(), this.radius.radius2(), this.radius.radius3(), this.radius.radius4(),
                this.color.color1(), this.color.color2(), this.color.color3(), this.color.color4(),
                this.smoothness, this.u, this.v, this.texWidth, this.texHeight, this.textureId);
    }

    public static void renderFast(Matrix4f matrix, float x, float y, float z,
                                  float width, float height,
                                  float radius1, float radius2, float radius3, float radius4,
                                  int color1, int color2, int color3, int color4,
                                  float smoothness, float u, float v, float texWidth, float texHeight,
                                  int textureId) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, textureId);

        ShaderProgram shader = RenderSystem.setShader(TEXTURE_SHADER_KEY);
        cacheUniforms(shader);
        sizeUniform.set(width, height);
        radiusUniform.set(radius1, radius2, radius3, radius4);
        smoothnessUniform.set(smoothness);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, x, y, z).texture(u, v).color(color1);
        builder.vertex(matrix, x, y + height, z).texture(u, v + texHeight).color(color2);
        builder.vertex(matrix, x + width, y + height, z).texture(u + texWidth, v + texHeight).color(color3);
        builder.vertex(matrix, x + width, y, z).texture(u + texWidth, v).color(color4);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void cacheUniforms(ShaderProgram shader) {
        if (cachedShader == shader) return;
        cachedShader = shader;
        sizeUniform = shader.getUniform("Size");
        radiusUniform = shader.getUniform("Radius");
        smoothnessUniform = shader.getUniform("Smoothness");
    }
}
