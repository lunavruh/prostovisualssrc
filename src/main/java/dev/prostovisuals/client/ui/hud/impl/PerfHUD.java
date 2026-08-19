package dev.prostovisuals.client.ui.hud.impl;

import dev.prostovisuals.client.managers.ThemeManager;

import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.util.perf.Perf;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;

import java.awt.*;
import java.util.List;

public class PerfHUD extends HudElement {
    private static final float WIDTH = 180f;
    private static final float ROW_H = 10f;
    private static final float PADDING = 4f;

    public PerfHUD() {
        super("Perf");
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;
        var matrices = e.getContext().getMatrices();

        Perf.endFrame();

        List<Perf.Row> rows = Perf.snapshot();
        int max = Math.min(10, rows.size());
        float h = ROW_H * (max + 1) + PADDING * 2;
        float originX = getX();
        float originY = getY();
        float hudScale = getHudScale();
        setBounds(originX, originY, WIDTH * hudScale, h * hudScale);
        pushHudScale(e.getContext(), originX, originY);

        float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
        LiquidGlassUtil.drawLiquidGlass(
                e.getContext(), getX(), getY(), WIDTH, h, liquidTime,
                4.5f, 0.14f, 0.0014f, 7.0f, 0.46f, 1.028f
        );

        float x = getX() + PADDING;
        float y = getY() + PADDING;
        float size = 7.5f;
        Render2D.drawFont(matrices, Fonts.REGULAR.getFont(size), "Section", x, y, new Color(
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getRed(),
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getGreen(),
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getBlue(),
                255));
        Render2D.drawFont(matrices, Fonts.REGULAR.getFont(size), "CPU ms % | GPU ms %", x + 82f, y, Color.LIGHT_GRAY);
        y += ROW_H;

        for (int i = 0; i < max; i++) {
            var r = rows.get(i);
            String left = r.name;
            String right = String.format("%.2f %2.0f | %.2f %2.0f", r.cpuMs, r.cpuPct, r.gpuMs, r.gpuPct);
            Render2D.drawFont(matrices, Fonts.REGULAR.getFont(size), left, x, y, new Color(
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getRed(),
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getGreen(),
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getBlue(),
                255));
            float rw = Fonts.REGULAR.getWidth(right, size);
            Render2D.drawFont(matrices, Fonts.REGULAR.getFont(size), right, getX() + WIDTH - PADDING - rw, y, new Color(
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getRed(),
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getGreen(),
                ThemeManager.getInstance().getCurrentTheme().getAccentColor().getBlue(),
                255));
            y += ROW_H;
        }

        popHudScale(e.getContext());
        super.onRender2D(e);
    }
}
