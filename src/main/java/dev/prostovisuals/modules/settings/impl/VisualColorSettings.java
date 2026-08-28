package dev.prostovisuals.modules.settings.impl;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.modules.settings.Setting;

import java.awt.Color;
import java.util.List;

/** Shared Theme/Custom color pair for world and HUD visual modules. */
public final class VisualColorSettings {
    private static final int DEFAULT_CUSTOM_COLOR = new Color(120, 205, 255).getRGB();

    private final BooleanSetting customColor = new BooleanSetting("Custom Color", false);
    private final ColorSetting color = new ColorSetting("Color", DEFAULT_CUSTOM_COLOR);

    public VisualColorSettings() {
        color.setVisible(customColor::getValue);
    }

    public List<Setting<?>> settings() {
        return List.of(customColor, color);
    }

    public Color resolve() {
        Color selected = customColor.getValue()
                ? color.getColor()
                : ThemeManager.getInstance().getRenderedAccentColor();
        return new Color(selected.getRed(), selected.getGreen(), selected.getBlue());
    }

    public Color resolve(int alpha) {
        Color selected = resolve();
        return new Color(selected.getRed(), selected.getGreen(), selected.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    public boolean isCustom() {
        return customColor.getValue();
    }

    public ColorSetting getColorSetting() {
        return color;
    }
}
