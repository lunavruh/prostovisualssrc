package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;

/**
 * ProstoVisual-facing controls for Wyvern's native cosmetics renderer.
 * The original Wyvern Cosmetics module is not replaced; this only exposes it
 * in the ProstoVisual ClickGUI and synchronizes its native settings.
 */
public final class WyvernCosmetics extends Module {
    private final BooleanSetting halo = new BooleanSetting("Halo", true);
    private final BooleanSetting wings = new BooleanSetting("Wings", true);
    private final BooleanSetting wingsAnimation = new BooleanSetting("Wings Animation", true, () -> wings.getValue());
    private final NumberSetting wingsSize = new NumberSetting("Wings Size", 1.0f, 0.65f, 1.8f, 0.05f, () -> wings.getValue());
    private final BooleanSetting classicWings = new BooleanSetting("Classic Wings", false);
    private final BooleanSetting classicAnimation = new BooleanSetting("Classic Animation", true, () -> classicWings.getValue());
    private final NumberSetting classicSize = new NumberSetting("Classic Size", 1.0f, 0.65f, 1.8f, 0.05f, () -> classicWings.getValue());
    private final BooleanSetting chinaHat = new BooleanSetting("Wyvern China Hat", true);

    public WyvernCosmetics() {
        super("WyvernCosmetics", Category.Render, "Wyvern native wings, halo and hat cosmetics.");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        sync(true);
    }

    @Override
    public void onDisable() {
        try { wtf.wyvern.client.modules.impl.render.Cosmetics.INSTANCE.setToggled(false); } catch (Throwable ignored) {}
        super.onDisable();
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        sync(false);
    }

    private void sync(boolean forceEnable) {
        try {
            wtf.wyvern.client.modules.impl.render.Cosmetics nativeCosmetics = wtf.wyvern.client.modules.impl.render.Cosmetics.INSTANCE;
            if (forceEnable && !nativeCosmetics.isEnabled()) nativeCosmetics.setToggled(true);
            nativeCosmetics.setCosmeticEnabled("Нимб", halo.getValue());
            nativeCosmetics.setCosmeticEnabled("Крылья", wings.getValue());
            nativeCosmetics.setCosmeticEnabled("Крылья 2", classicWings.getValue());
            nativeCosmetics.setCosmeticEnabled("Китайская шляпа", chinaHat.getValue());
            nativeCosmetics.setButterflyWingAnimation(wingsAnimation.getValue());
            nativeCosmetics.setButterflyWingSize(wingsSize.getValue());
            nativeCosmetics.setClassicWingAnimation(classicAnimation.getValue());
            nativeCosmetics.setClassicWingSize(classicSize.getValue());
        } catch (Throwable ignored) {}
    }
}
