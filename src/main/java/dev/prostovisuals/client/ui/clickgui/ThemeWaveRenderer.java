package dev.prostovisuals.client.ui.clickgui;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.util.renderer.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * Full-screen radial glass wave used by client-wide theme transitions.
 *
 * This deliberately does not touch LiquidGlassUtil or its framebuffer/shader.
 * The moving lens is built as several distorted translucent rings, producing a
 * water/caustic front while remaining compatible with every existing screen.
 */
public final class ThemeWaveRenderer {
    private static final int SEGMENTS = 88;

    private ThemeWaveRenderer() {}

    public static void render(DrawContext context, float time) {
        ThemeManager themes = ThemeManager.getInstance();
        if (!themes.isTransitioning()) return;

        float progress = themes.getTransitionProgress();
        float radius = themes.getTransitionRadius();
        float band = themes.getTransitionBand();
        float cx = themes.getTransitionCenterX();
        float cy = themes.getTransitionCenterY();
        float strength = (float) Math.sin(Math.PI * progress);
        if (radius <= -band || strength <= 0.001f) return;

        Color target = themes.getTransitionTargetAccent();
        float lensRadius = Math.max(0.0f, radius - band * 0.42f);
        if (lensRadius > 1.0f) {
            // A very light refractive wash follows the front. It is deliberately
            // subtle: the real color change comes from ThemeManager sampling.
            Render2D.drawRoundedRect(context.getMatrices(),
                    cx - lensRadius, cy - lensRadius,
                    lensRadius * 2.0f, lensRadius * 2.0f, lensRadius,
                    new Color(target.getRed(), target.getGreen(), target.getBlue(),
                            Math.max(0, Math.min(18, Math.round(12.0f * strength)))));
        }

        // Broad translucent body: unlike the old single vertical line this is
        // a real moving band with independent ripples and caustic highlights.
        float[] offsets = {-20.0f, -13.0f, -7.0f, -2.0f, 4.0f, 10.0f, 18.0f};
        float[] weights = {0.13f, 0.24f, 0.48f, 1.00f, 0.64f, 0.31f, 0.14f};
        for (int i = 0; i < offsets.length; i++) {
            int alpha = Math.max(0, Math.min(255, Math.round(150.0f * weights[i] * strength)));
            Color color = i == 3
                    ? new Color(238, 250, 255, alpha)
                    : new Color(target.getRed(), target.getGreen(), target.getBlue(), alpha);
            drawDistortedRing(context, cx, cy, radius + offsets[i], time,
                    0.75f + weights[i] * 0.95f, color, i * 0.71f);
        }

        // Secondary waves behind the main crest make it read as displaced
        // water/glass rather than a glowing outline.
        drawDistortedRing(context, cx, cy, radius - band * 0.92f, time,
                0.65f, new Color(target.getRed(), target.getGreen(), target.getBlue(),
                        Math.round(42.0f * strength)), 1.7f);
        drawDistortedRing(context, cx, cy, radius - band * 1.55f, time,
                0.52f, new Color(235, 249, 255, Math.round(24.0f * strength)), 2.9f);
    }

    private static void drawDistortedRing(DrawContext context, float cx, float cy,
                                          float radius, float time, float width,
                                          Color color, float phase) {
        if (radius <= 1.0f || color.getAlpha() <= 0) return;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / SEGMENTS);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / SEGMENTS);
            float r0 = radius + distortion(a0, time, phase);
            float r1 = radius + distortion(a1, time, phase);
            float x0 = cx + (float) Math.cos(a0) * r0;
            float y0 = cy + (float) Math.sin(a0) * r0;
            float x1 = cx + (float) Math.cos(a1) * r1;
            float y1 = cy + (float) Math.sin(a1) * r1;

            // Small angular brightness changes imitate caustic reflections.
            float caustic = 0.72f + 0.28f * (float) Math.sin(a0 * 5.0f - time * 3.4f + phase);
            int alpha = Math.max(0, Math.min(255, Math.round(color.getAlpha() * caustic)));
            Render2D.drawLine(context.getMatrices(), x0, y0, x1, y1, width,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        }
    }

    private static float distortion(float angle, float time, float phase) {
        return (float) Math.sin(angle * 3.0f + time * 2.8f + phase) * 2.8f
                + (float) Math.sin(angle * 7.0f - time * 1.9f + phase * 1.7f) * 1.25f
                + (float) Math.sin(angle * 11.0f + time * 1.15f) * 0.55f;
    }
}
