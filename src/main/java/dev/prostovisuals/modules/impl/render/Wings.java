package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;

/** ProstoVisual-facing controller for the original Wyvern wing renderers. */
public final class Wings extends Module {
    public enum Style implements Nameable {
        BUTTERFLY("Butterfly"),
        CLASSIC("Classic"),
        COMBINED("Combined");

        private final String name;
        Style(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }

    private final EnumSetting<Style> style = new EnumSetting<>("Style", Style.BUTTERFLY);
    private final BooleanSetting animation = new BooleanSetting("Animation", true);
    private final NumberSetting size = new NumberSetting("Size", 1.0f, 0.65f, 1.8f, 0.05f);

    public Wings() {
        super("Wings", Category.Render, "Wyvern wings as a standalone render module");
        getSettings().add(style);
        getSettings().add(animation);
        getSettings().add(size);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        syncRenderer();
    }

    @Override
    public void onDisable() {
        var nativeWings = wtf.wyvern.client.modules.impl.render.Cosmetics.INSTANCE;
        nativeWings.setCosmeticEnabled("Крылья", false);
        nativeWings.setCosmeticEnabled("Крылья 2", false);
        if (nativeWings.isEnabled()) nativeWings.setToggled(false);
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick event) {
        syncRenderer();
    }

    private void syncRenderer() {
        var nativeWings = wtf.wyvern.client.modules.impl.render.Cosmetics.INSTANCE;
        if (!nativeWings.isEnabled()) nativeWings.setToggled(true);

        Style current = style.getValue();
        boolean butterfly = current == Style.BUTTERFLY || current == Style.COMBINED;
        boolean classic = current == Style.CLASSIC || current == Style.COMBINED;
        nativeWings.setCosmeticEnabled("Крылья", butterfly);
        nativeWings.setCosmeticEnabled("Крылья 2", classic);
        nativeWings.setButterflyWingAnimation(animation.getValue());
        nativeWings.setClassicWingAnimation(animation.getValue());
        nativeWings.setButterflyWingSize(size.getValue());
        nativeWings.setClassicWingSize(size.getValue());
    }
}
