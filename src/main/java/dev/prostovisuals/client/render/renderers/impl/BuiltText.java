package dev.prostovisuals.client.render.renderers.impl;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.prostovisuals.client.render.msdf.MsdfFont;
import dev.prostovisuals.client.render.providers.ResourceProvider;
import dev.prostovisuals.client.render.renderers.IRenderer;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

public record BuiltText(
        MsdfFont font,
        String text,
    	float size,
        float thickness,
        int color,
		float smoothness,
        float spacing,
		int outlineColor,
		float outlineThickness
    ) implements IRenderer {

	private static final ShaderProgramKey MSDF_FONT_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("msdf_font"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
	private static ShaderProgram cachedShader;
	private static GlUniform rangeUniform, thicknessUniform, smoothnessUniform, outlineUniform;
	private static GlUniform outlineThicknessUniform, outlineColorUniform;
	
	@Override
    public void render(Matrix4f matrix, float x, float y, float z) {
		renderFast(matrix, x, y, z, this.font, this.text, this.size, this.thickness,
				this.color, this.smoothness, this.spacing, this.outlineColor, this.outlineThickness);
	}

	public static void renderFast(Matrix4f matrix, float x, float y, float z,
							  MsdfFont font, String text, float size, float thickness,
							  int color, float smoothness, float spacing,
							  int outlineColor, float outlineThickness) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShaderTexture(0, font.getTextureId());
		
		boolean outlineEnabled = (outlineThickness > 0.0f);

		ShaderProgram shader = RenderSystem.setShader(MSDF_FONT_SHADER_KEY);
		cacheUniforms(shader);
		rangeUniform.set(font.getAtlas().range());
		thicknessUniform.set(thickness);
		smoothnessUniform.set(smoothness);
		outlineUniform.set(outlineEnabled ? 1 : 0);

		if (outlineEnabled) {
			outlineThicknessUniform.set(outlineThickness);
			outlineColorUniform.set(
					((outlineColor >> 16) & 0xFF) / 255.0f,
					((outlineColor >> 8) & 0xFF) / 255.0f,
					(outlineColor & 0xFF) / 255.0f,
					((outlineColor >>> 24) & 0xFF) / 255.0f);
		}
		
		BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		font.applyGlyphs(matrix, builder, text, size,
				(thickness + outlineThickness * 0.5f) * 0.5f * size,
				spacing, x, y + font.getMetrics().baselineHeight() * size, z, color);
		BufferRenderer.drawWithGlobalProgram(builder.end());
		RenderSystem.setShaderTexture(0, 0);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void cacheUniforms(ShaderProgram shader) {
		if (cachedShader == shader) return;
		cachedShader = shader;
		rangeUniform = shader.getUniform("Range");
		thicknessUniform = shader.getUniform("Thickness");
		smoothnessUniform = shader.getUniform("Smoothness");
		outlineUniform = shader.getUniform("Outline");
		outlineThicknessUniform = shader.getUniform("OutlineThickness");
		outlineColorUniform = shader.getUniform("OutlineColor");
	}
}
