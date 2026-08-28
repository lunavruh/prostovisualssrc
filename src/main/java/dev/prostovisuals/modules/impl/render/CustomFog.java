package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.ColorSetting;
import net.minecraft.client.resource.language.I18n;

import java.awt.Color;

public class CustomFog extends Module {
    private final ThemeManager themeManager;

    private final NumberSetting fogDistance = new NumberSetting(
            "setting.fogDistance", 64.0f, 8.0f, 256.0f, 1.0f
    );
    private final NumberSetting fogStart = new NumberSetting(
            "Fog Start", 58.0f, 20.0f, 90.0f, 1.0f
    );
    private final BooleanSetting customColor = new BooleanSetting("Custom Color", false);
    private final ColorSetting fogColor = new ColorSetting("Fog Color", new Color(40, 92, 140).getRGB());
    private final BooleanSetting fogBlur = new BooleanSetting("Fog Blur", false);
    private final NumberSetting fogBlurStrength = new NumberSetting("Fog Blur Strength", 0.32f, 0.05f, 0.80f, 0.05f, fogBlur::getValue);

    public CustomFog() {
        super("CustomFog", Category.Render, I18n.translate("module.customfog.description"));
        this.themeManager = ThemeManager.getInstance();
        fogColor.setVisible(customColor::getValue);
    }

    public Color getSkyColor() {
        if (customColor.getValue()) {
            Color c = fogColor.getColor();
            return new Color(c.getRed(), c.getGreen(), c.getBlue());
        }
        return themeManager.getRenderedBackgroundColor();
    }

    public Color getSkyColorSecondary() {
        return themeManager.getRenderedSecondaryBackgroundColor();
    }

    public float getFogDistance() {
        return fogDistance.getValue();
    }

    public float getFogStartDistance() {
        return getFogDistance() * (fogStart.getValue() / 100.0f);
    }

    public boolean isFogBlurEnabled() {
        return fogBlur.getValue();
    }

    public float getFogBlurStrength() {
        return fogBlurStrength.getValue();
    }
}
