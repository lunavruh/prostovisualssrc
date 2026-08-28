package dev.prostovisuals.modules.impl.utility;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.util.DiscordRichPresenceUtil;
import net.minecraft.client.resource.language.I18n;

public class DiscordRPCModule extends Module {
    public DiscordRPCModule() {
        super("DiscordRPC", Category.Utility, I18n.translate("module.discordrpc.description"));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            DiscordRichPresenceUtil.discordrpc();
        } catch (Throwable t) {
            // не роняем игру, если библиотеки RPC нет или Discord недоступен — просто выключаем модуль
            prostovisuals.LOGGER.error("[prostovisuals] Не удалось запустить Discord RPC, модуль выключен.", t);
            super.onDisable();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        try {
            DiscordRichPresenceUtil.shutdownDiscord();
        } catch (Throwable t) {
            prostovisuals.LOGGER.error("[prostovisuals] Ошибка при остановке Discord RPC.", t);
        }
    }
}
