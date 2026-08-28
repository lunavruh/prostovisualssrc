package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;

import java.awt.Color;

public class HitColor extends Module {

    public final NumberSetting alpha = new NumberSetting("setting.alpha", 0.8f, 0.1f, 1.0f, 0.05f);
    private final VisualColorSettings visualColor = new VisualColorSettings();

    public HitColor() {
        super("HitColor", Category.Render, "module.hitcolor.description");
    }

    public Color getRenderColor() {
        return visualColor.resolve();
    }
}
