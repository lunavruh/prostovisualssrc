package dev.prostovisuals.client.ui.colorgui;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.clickgui.OneClientClickGui;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.settings.impl.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;

/** Compact, continuously rendered HSV picker used by every visual color setting. */
public final class ColorPickerScreen extends Screen {
    private static final float PANEL_WIDTH = 320.0f;
    private static final float PANEL_HEIGHT = 220.0f;
    private static final float HUE_WIDTH = 12.0f;

    private final ColorSetting setting;
    private final Screen parent;
    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1L;

    private float hue;
    private float saturation;
    private float brightness;
    private boolean draggingSquare;
    private boolean draggingHue;

    // Rendering and input share this layout, preventing the old click offset.
    private float panelX, panelY, panelW, panelH;
    private float squareX, squareY, squareW, squareH;
    private float hueX, hueY, hueH;
    private float sideX, sideW;

    public ColorPickerScreen(ColorSetting setting) {
        this(setting, null);
    }

    public ColorPickerScreen(ColorSetting setting, Screen parent) {
        super(Text.of("Color Picker"));
        this.setting = setting;
        this.parent = parent;

        Color color = setting.getColor();
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateLayout();

        var matrices = context.getMatrices();
        float animation = animationProgress();
        if (closingAt >= 0L && animation <= 0.01f) {
            if (client != null) client.setScreen(parent);
            return;
        }
        Color accent = ThemeManager.getInstance().getRenderedAccentColor();
        Color text = ThemeManager.getInstance().getRenderedTextColor();
        Color selected = Color.getHSBColor(hue, saturation, brightness);

        Render2D.drawRect(matrices, 0, 0, width, height, new Color(3, 8, 13, Math.round(94 * animation)));
        float centerX = panelX + panelW * 0.5f;
        float centerY = panelY + panelH * 0.5f;
        float scale = 0.94f + 0.06f * animation;
        matrices.push();
        matrices.translate(centerX, centerY, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-centerX, -centerY, 0.0f);
        Render2D.drawBlurredRect(matrices, panelX, panelY, panelW, panelH, 11.0f, 8.0f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 38));
        Render2D.drawRoundedRect(matrices, panelX, panelY, panelW, panelH, 11.0f,
                new Color(13, 25, 35, 242));
        Render2D.drawBorder(matrices, panelX, panelY, panelW, panelH, 11.0f, 0.16f, 0.34f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 105));

        Render2D.drawFont(matrices, Fonts.BOLD.getFont(8.8f), "Color Picker",
                panelX + 14.0f, panelY + 11.0f, withAlpha(text, 255));
        Render2D.drawFont(matrices, Fonts.REGULAR.getFont(5.2f), "Drag to choose a custom color",
                panelX + 14.0f, panelY + 25.0f, new Color(191, 207, 218, 195));

        boolean closeHovered = hovered(mouseX, mouseY, panelX + panelW - 27.0f, panelY + 9.0f, 17.0f, 17.0f);
        Render2D.drawRoundedRect(matrices, panelX + panelW - 27.0f, panelY + 9.0f, 17.0f, 17.0f, 5.0f,
                closeHovered ? new Color(232, 241, 247, 34) : new Color(232, 241, 247, 16));
        Render2D.drawFont(matrices, Fonts.MEDIUM.getFont(8.0f), "x",
                panelX + panelW - 21.5f, panelY + 12.2f, new Color(232, 241, 247, 230));

        drawSaturationBrightness(context);
        drawHue(context);

        Render2D.drawRoundedRect(matrices, sideX, squareY, sideW, 52.0f, 7.0f,
                new Color(229, 241, 248, 14));
        Render2D.drawBorder(matrices, sideX, squareY, sideW, 52.0f, 7.0f, 0.14f, 0.28f,
                new Color(255, 255, 255, 40));
        Render2D.drawRoundedRect(matrices, sideX + 5.0f, squareY + 5.0f, sideW - 10.0f, 42.0f, 5.0f,
                selected);

        int rgb = selected.getRGB();
        String hex = String.format("#%06X", rgb & 0xFFFFFF);
        Render2D.drawFont(matrices, Fonts.BOLD.getFont(6.8f), hex,
                sideX, squareY + 61.0f, withAlpha(text, 245));
        Render2D.drawFont(matrices, Fonts.REGULAR.getFont(5.2f),
                "R " + selected.getRed() + "  G " + selected.getGreen() + "  B " + selected.getBlue(),
                sideX, squareY + 75.0f, new Color(194, 210, 221, 215));

        float doneY = panelY + panelH - 31.0f;
        boolean doneHovered = hovered(mouseX, mouseY, sideX, doneY, sideW, 20.0f);
        Color doneColor = OneClientClickGui.referenceAccentColorAt(sideX + sideW * 0.5f, doneY + 10.0f);
        Render2D.drawRoundedRect(matrices, sideX, doneY, sideW, 20.0f, 6.0f,
                new Color(doneColor.getRed(), doneColor.getGreen(), doneColor.getBlue(), doneHovered ? 225 : 185));
        float doneTextWidth = Fonts.BOLD.getFont(6.2f).getWidth("Done");
        Render2D.drawFont(matrices, Fonts.BOLD.getFont(6.2f), "Done",
                sideX + Math.max(7.0f, (sideW - doneTextWidth) * 0.5f), doneY + 6.2f, Color.WHITE);
        matrices.pop();
    }

    private void drawSaturationBrightness(DrawContext context) {
        var matrices = context.getMatrices();
        Color pureHue = Color.getHSBColor(hue, 1.0f, 1.0f);

        // Two GPU gradients produce a smooth HSV field without thousands of tiny rectangles.
        Render2D.drawGradientRect(matrices, squareX, squareY, squareW, squareH,
                Color.WHITE, pureHue, true);
        Render2D.drawGradientRect(matrices, squareX, squareY, squareW, squareH,
                new Color(0, 0, 0, 0), Color.BLACK, false);
        Render2D.drawBorder(matrices, squareX, squareY, squareW, squareH, 3.0f, 0.1f, 0.24f,
                new Color(255, 255, 255, 75));

        float markerX = squareX + saturation * squareW;
        float markerY = squareY + (1.0f - brightness) * squareH;
        Render2D.drawRoundedRect(matrices, markerX - 4.5f, markerY - 4.5f, 9.0f, 9.0f, 4.5f,
                new Color(10, 16, 21, 220));
        Render2D.drawRoundedRect(matrices, markerX - 3.0f, markerY - 3.0f, 6.0f, 6.0f, 3.0f,
                Color.WHITE);
        Render2D.drawRoundedRect(matrices, markerX - 1.7f, markerY - 1.7f, 3.4f, 3.4f, 1.7f,
                Color.getHSBColor(hue, saturation, brightness));
    }

    private void drawHue(DrawContext context) {
        var matrices = context.getMatrices();
        Color[] stops = {
                new Color(255, 0, 0), new Color(255, 255, 0), new Color(0, 255, 0),
                new Color(0, 255, 255), new Color(0, 0, 255), new Color(255, 0, 255),
                new Color(255, 0, 0)
        };
        float segment = hueH / 6.0f;
        for (int i = 0; i < 6; i++) {
            Render2D.drawGradientRect(matrices, hueX, hueY + segment * i, HUE_WIDTH,
                    i == 5 ? hueH - segment * i : segment + 0.25f,
                    stops[i], stops[i + 1], false);
        }
        Render2D.drawBorder(matrices, hueX, hueY, HUE_WIDTH, hueH, 4.0f, 0.1f, 0.24f,
                new Color(255, 255, 255, 80));

        float markerY = hueY + hue * hueH;
        Render2D.drawRoundedRect(matrices, hueX - 2.5f, markerY - 2.0f, HUE_WIDTH + 5.0f, 4.0f, 2.0f,
                new Color(8, 14, 19, 230));
        Render2D.drawRoundedRect(matrices, hueX - 1.2f, markerY - 0.8f, HUE_WIDTH + 2.4f, 1.6f, 0.8f,
                Color.WHITE);
    }

    private void updateLayout() {
        panelW = Math.min(PANEL_WIDTH, Math.max(220.0f, width - 24.0f));
        panelH = Math.min(PANEL_HEIGHT, Math.max(186.0f, height - 24.0f));
        panelX = (width - panelW) * 0.5f;
        panelY = (height - panelH) * 0.5f;

        squareX = panelX + 14.0f;
        squareY = panelY + 42.0f;
        squareH = panelH - 56.0f;
        sideW = Math.max(68.0f, Math.min(82.0f, panelW * 0.27f));
        squareW = Math.max(92.0f, panelW - 14.0f - 8.0f - HUE_WIDTH - 11.0f - sideW - 14.0f);
        hueX = squareX + squareW + 8.0f;
        hueY = squareY;
        hueH = squareH;
        sideX = hueX + HUE_WIDTH + 11.0f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateLayout();
        if (animationProgress() < 0.98f) return true;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (hovered(mouseX, mouseY, panelX + panelW - 27.0f, panelY + 9.0f, 17.0f, 17.0f)
                || hovered(mouseX, mouseY, sideX, panelY + panelH - 31.0f, sideW, 20.0f)) {
            close();
            return true;
        }
        if (hovered(mouseX, mouseY, squareX, squareY, squareW, squareH)) {
            draggingSquare = true;
            updateSquare(mouseX, mouseY);
            return true;
        }
        if (hovered(mouseX, mouseY, hueX, hueY, HUE_WIDTH, hueH)) {
            draggingHue = true;
            updateHue(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingSquare) {
            updateSquare(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSquare = false;
        draggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSquare(double mouseX, double mouseY) {
        saturation = clamp01((float) ((mouseX - squareX) / squareW));
        brightness = 1.0f - clamp01((float) ((mouseY - squareY) / squareH));
        updateColor();
    }

    private void updateHue(double mouseY) {
        hue = clamp01((float) ((mouseY - hueY) / hueH));
        updateColor();
    }

    private void updateColor() {
        setting.set(Color.getHSBColor(hue, saturation, brightness).getRGB());
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private float animationProgress() {
        long now = System.currentTimeMillis();
        if (closingAt >= 0L) {
            float t = clamp01((now - closingAt) / 135.0f);
            float eased = 1.0f - (float) Math.pow(1.0f - t, 3.0);
            return 1.0f - eased;
        }
        float t = clamp01((now - openedAt) / 170.0f);
        return 1.0f - (float) Math.pow(1.0f - t, 3.0);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static boolean hovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void close() {
        if (closingAt < 0L) closingAt = System.currentTimeMillis();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally transparent: this is a compact ClickGUI modal.
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
