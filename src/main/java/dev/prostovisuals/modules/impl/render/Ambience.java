package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;

/** ProstoVisual UI wrapper around Wyvern's original ambience renderer.
 *  The actual time/fog implementation remains Wyvern code so visuals stay identical. */
public final class Ambience extends Module {
    private final BooleanSetting customTime = new BooleanSetting("Custom Time", true);
    private final NumberSetting time = new NumberSetting("World Time", 18.0f, 0.0f, 24.0f, 0.1f, customTime::getValue);
    private final BooleanSetting customFog = new BooleanSetting("Custom Fog", true);
    private final NumberSetting fogDistance = new NumberSetting("Fog End Distance", 120f, 0f, 500f, 5f, customFog::getValue);
    private final NumberSetting fogStart = new NumberSetting("Fog Start Distance", 18f, 0f, 480f, 1f, customFog::getValue);
    private final NumberSetting fogSaturation = new NumberSetting("Fog Saturation", 0.50f, 0f, 1f, 0.05f, customFog::getValue);

    public Ambience() {
        super("Ambience", Category.Render, "Wyvern world atmosphere: time and fog, integrated into ProstoVisual");
    }

    @Override public void onEnable() {
        super.onEnable();
        sync();
        var nativeModule = wtf.wyvern.client.modules.impl.render.Ambience.INSTANCE;
        if (!nativeModule.isEnabled()) nativeModule.setToggled(true);
    }

    @Override public void onDisable() {
        var nativeModule = wtf.wyvern.client.modules.impl.render.Ambience.INSTANCE;
        if (nativeModule.isEnabled()) nativeModule.setToggled(false);
        super.onDisable();
    }

    @EventHandler public void onTick(EventTick e) { sync(); }

    private void sync() {
        var n = wtf.wyvern.client.modules.impl.render.Ambience.INSTANCE;
        n.customTime.setEnabled(customTime.getValue());
        n.timeSetting.setCurrent(time.getValue());
        n.customFog.setEnabled(customFog.getValue());
        n.distanceSetting.setCurrent(fogDistance.getValue());
        n.startMultiplier.setCurrent(fogStart.getValue());
        n.colorIntensity.setCurrent(fogSaturation.getValue());
    }
}
