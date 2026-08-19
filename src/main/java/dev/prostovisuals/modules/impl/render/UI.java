package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.ChatUtils;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.client.ui.clickgui.ClickGui;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.resource.language.I18n;

public class UI extends Module {


    public UI() {
        super("UI", Category.Render, I18n.translate("module.ui.description"));
        setBind(new Bind(GLFW.GLFW_KEY_RIGHT_SHIFT, false));

    }

    @EventHandler
    public void onTick(EventTick e) {
        if (!(mc.currentScreen instanceof ClickGui) && !(mc.currentScreen instanceof ClickGui)) {
            setToggled(false);
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();

		// Allow opening only when in a world
		if (mc.player == null || mc.world == null) {
			ChatUtils.sendMessage(I18n.translate("prostovisuals.ui.onlyInWorld"));
			setToggled(false);
			return;
		}

		mc.setScreen(prostovisuals.getInstance().getClickGui());


    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.currentScreen instanceof ClickGui) {
            ((ClickGui) mc.currentScreen).close();
        }
    }
}