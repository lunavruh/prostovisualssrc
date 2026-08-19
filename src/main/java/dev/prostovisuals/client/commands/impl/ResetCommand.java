package dev.prostovisuals.client.commands.impl;

import dev.prostovisuals.client.commands.Command;
import dev.prostovisuals.client.ChatUtils;
import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.client.ui.hud.HudElement;
import net.minecraft.command.CommandSource;
import net.minecraft.client.resource.language.I18n;
import dev.prostovisuals.client.managers.ThemeManager;

public class ResetCommand extends Command {
    private static long lastRequestMs = 0L;
    private static final long CONFIRM_WINDOW_MS = 10_000L;
    public ResetCommand() {
        super("reset");
    }

    @Override
    public void execute(com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            long now = System.currentTimeMillis();
            if (now - lastRequestMs > CONFIRM_WINDOW_MS) {
                lastRequestMs = now;
                ChatUtils.sendMessage(I18n.translate("cmd.reset.confirm"));
                return 1;
            }

            // сбрасываем префикс на точку (команда .reset всегда доступна через точку)
            prostovisuals.getInstance().getCommandManager().setPrefix(".");

            // Сбрасываем все модули
            for (Module module : prostovisuals.getInstance().getModuleManager().getModules()) {
                if (module.isToggled()) module.setToggled(false);
                for (Setting<?> setting : module.getSettings()) {
                    setting.reset();
                }
            }

            // Сбрасываем бинды к изначальным
            prostovisuals.getInstance().getModuleManager().resetBindsToDefaults();

            // Сбрасываем HUD
            for (HudElement hud : prostovisuals.getInstance().getHudManager().getHudElements()) {
                for (Setting<?> setting : hud.getSettings()) {
                    setting.reset();
                }
                if (!hud.isToggled()) hud.setToggled(true);
            }

            // Сбрасываем тему на дефолтную
            try {
                ThemeManager.getInstance().setTheme(new ThemeManager.LightTheme());
            } catch (Throwable ignored) {}

            ChatUtils.sendMessage(I18n.translate("cmd.reset.done"));

            try {
                prostovisuals.getInstance().getAutoSaveManager().forceSave();
            } catch (Throwable ignored) {}
            lastRequestMs = 0L;
            return 1;
        });
    }
}
