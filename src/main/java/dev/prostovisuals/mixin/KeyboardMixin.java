package dev.prostovisuals.mixin;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.events.impl.EventKey;
import dev.prostovisuals.client.spatial.SpatialDisplayManager;
import dev.prostovisuals.modules.impl.render.BetterMinecraft;
import dev.prostovisuals.client.ui.hud.impl.PerfHUD;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.ui.hud.PauseHudGate;
import dev.prostovisuals.client.util.perf.Perf;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

	@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
	public void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		EventKey event = new EventKey(key, action, modifiers);
		prostovisuals.getInstance().getEventHandler().post(event);

		// Hide custom glass HUD before Minecraft builds the first Esc/pause blur frame.
		if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
			PauseHudGate.armForPauseTransition();
		}

		// After clicking a Spatial Display, keyboard input belongs to that app until Esc.
		if (SpatialDisplayManager.getInstance().handleKey(key, action, modifiers)) {
			ci.cancel();
			return;
		}

		// Ctrl + Shift + Q: toggle Perf HUD overlay (spawn on demand, not listed in elements)
		if (key == GLFW.GLFW_KEY_Q && action == GLFW.GLFW_PRESS
				&& (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
				&& (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
			try {
				var hudManager = prostovisuals.getInstance().getHudManager();
				if (hudManager != null) {
					PerfHUD perfHud = null;
					for (HudElement he : hudManager.getHudElements()) {
						if (he instanceof PerfHUD p) { perfHud = p; break; }
					}
					if (perfHud == null) {
						perfHud = new PerfHUD();
						// Set a default position near top-left if needed
						perfHud.setBounds(10, 10, 180, 120);
						hudManager.getHudElements().add(perfHud);
					} else {
						perfHud.setToggled(!perfHud.isToggled());
					}
					Perf.setEnabled(perfHud.isToggled());
				}
			} catch (Throwable ignored) {}
		}

		BetterMinecraft module = prostovisuals.getInstance().getModuleManager().getModule(BetterMinecraft.class);
		if (module == null || !module.isToggled()) return;

		// F5: сброс анимации отдаления при переключении
		if (key == GLFW.GLFW_KEY_F5 && action == GLFW.GLFW_PRESS && module.smoothThirdPersonZoom.getValue()) {
			module.getThirdPersonAnimation().reset();
		}

		// TAB: направление и сброс анимации открытия/закрытия
		if (key == GLFW.GLFW_KEY_TAB) {
			if (action == GLFW.GLFW_PRESS && module.smoothTab.getValue()) {
				module.setTabPressed(true);
				module.getTabOpenAnimation().reset();
				module.getTabOpenAnimation().update(true);
			} else if (action == GLFW.GLFW_RELEASE && module.smoothTab.getValue()) {
				module.setTabPressed(false);
				module.getTabOpenAnimation().reset();
				module.getTabOpenAnimation().update(false);
			}
		}
	}

	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
	private void onSpatialChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
		if (SpatialDisplayManager.getInstance().handleCharacter(codePoint)) ci.cancel();
	}

}
