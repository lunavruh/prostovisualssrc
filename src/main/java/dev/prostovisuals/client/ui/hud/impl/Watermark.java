package dev.prostovisuals.client.ui.hud.impl;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.util.perf.Perf;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Font;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;

import java.awt.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public class Watermark extends HudElement
        implements ThemeManager.ThemeChangeListener {

    private static final Identifier LOGO = Identifier.of("prostovisuals", "hud/amongus.png");
    private static final Identifier PING_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/ping.png");
    private static final Identifier FPS_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/fps.png");

    private final ThemeManager themeManager;

    private Color textColor;
    private Color accentColor;

    private float totalWidth;
    private float totalHeight;

    public Watermark() {
        super("Watermark");

        this.themeManager = ThemeManager.getInstance();

        applyTheme(themeManager.getCurrentTheme());
        themeManager.addThemeChangeListener(this);
    }

    private void applyTheme(ThemeManager.Theme theme) {
        Color c = theme.getAccentColor();
        this.textColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
        this.accentColor = theme.getAccentColor();
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) {
        applyTheme(theme);
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) {
            return;
        }

        Perf.tryBeginFrame();

        try (var __ = Perf.scopeCpu("Watermark.onRender2D")) {
            var matrices = e.getContext().getMatrices();

            Font font = Fonts.REGULAR;
            MinecraftClient mc = MinecraftClient.getInstance();

            String username =
                    mc.player != null
                            ? mc.player.getName().getString()
                            : "Username";

            int ping = getPing(mc);
            int fps = mc.getCurrentFps();

            String pingText = ping + " ms";
            String fpsText = fps + " FPS";

            float fontSize = 8.5f;
            float logoSize = 13.2f;
            float pingIconSize = 11.5f;
            float fpsIconW = 15.2f;
            float fpsIconH = 12.0f;
            float gap = 2.4f;
            float sectionGap = 4.0f;

            float usernameW = font.getWidth(username, fontSize);
            float pingW = font.getWidth(pingText, fontSize);
            float fpsW = font.getWidth(fpsText, fontSize);

            float contentWidth =
                    logoSize + gap + usernameW +
                    sectionGap + pingIconSize + gap + pingW +
                    sectionGap + fpsIconW + gap + fpsW;

            totalWidth = Math.max(148.0f, contentWidth + 12.0f);
            totalHeight = 21.0f;

            float screenWidth = mc.getWindow().getScaledWidth();
            float x = (screenWidth - totalWidth) / 2.0f;
            float y = 38.0f;

            float hudScale = getHudScale();
            setBounds(x, y, totalWidth * hudScale, totalHeight * hudScale);
            pushHudScale(e.getContext(), x, y);

            float liquidTime =
                    (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);

            LiquidGlassUtil.drawLiquidGlass(
                    e.getContext(),
                    x, y, totalWidth, totalHeight,
                    liquidTime
            );

            Color theme = themeManager.getCurrentTheme().getAccentColor();
            Color fullTheme = new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 255);

            float cursor = x + (totalWidth - contentWidth) * 0.5f;
            float logoY = y + (totalHeight - logoSize) * 0.5f;

            // Among Us is deliberately theme tinted.
            Render2D.drawTexture(
                    matrices, cursor, logoY,
                    logoSize, logoSize, 3.5f,
                    LOGO, fullTheme
            );
            cursor += logoSize + gap;

            float textY = y + (totalHeight - fontSize) * 0.5f - 0.5f;
            Render2D.drawFont(matrices, font.getFont(fontSize), username, cursor, textY, textColor);
            cursor += usernameW + sectionGap;

            float pingIconY = y + (totalHeight - pingIconSize) * 0.5f + 0.9f;
            Render2D.drawTexture(matrices, cursor, pingIconY,
                    pingIconSize, pingIconSize, 0.0f, PING_ICON, fullTheme);
            cursor += pingIconSize + gap;
            Render2D.drawFont(matrices, font.getFont(fontSize), pingText, cursor, textY, textColor);
            cursor += pingW + sectionGap;

            float fpsIconY = y + (totalHeight - fpsIconH) * 0.5f + 0.9f;
            Render2D.drawTexture(matrices, cursor, fpsIconY,
                    fpsIconW, fpsIconH, 0.0f, FPS_ICON, fullTheme);
            cursor += fpsIconW + gap;
            Render2D.drawFont(matrices, font.getFont(fontSize), fpsText, cursor, textY, textColor);
            popHudScale(e.getContext());
        }
    }

    private int getPing(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }
}
