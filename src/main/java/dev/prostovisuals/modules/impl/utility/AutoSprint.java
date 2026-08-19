package dev.prostovisuals.modules.impl.utility;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.client.events.impl.EventTick;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

public class AutoSprint extends Module {

    public AutoSprint() {
        super("AutoSprint", Category.Utility, I18n.translate("module.autosprint.description"));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.sprintKey.setPressed(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.sprintKey.setPressed(false);
        }
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (!isToggled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;
        // Имитация зажатой клавиши бега каждый тик
        client.options.sprintKey.setPressed(true);
    }
}