package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.render.renderers.MotionBlurRenderer;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;

/**
 * Camera reprojection motion blur.
 */
public final class MotionBlur extends Module {
    public final NumberSetting strength = new NumberSetting("Strength", 55.0f, 0.0f, 100.0f, 1.0f);

    public MotionBlur() {
        super("MotionBlur", Category.Render, "Motion blur on camera rotation");
    }

    public float getStrength() {
        return strength.getValue();
    }

    @Override
    public void onEnable() {
        MotionBlurRenderer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MotionBlurRenderer.reset();
    }
}
