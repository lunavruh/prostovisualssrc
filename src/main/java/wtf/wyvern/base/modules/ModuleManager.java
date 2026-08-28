package wtf.wyvern.base.modules;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.particle.Particle;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.events.impl.input.EventKey;
import wtf.wyvern.base.events.impl.render.EventHudRender;
import wtf.wyvern.base.events.impl.server.EventPacket;
import wtf.wyvern.base.macro.Macro;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.setting.Setting;
import wtf.wyvern.client.modules.api.setting.impl.BooleanSetting;
import wtf.wyvern.client.modules.api.setting.impl.ModeSetting;
import wtf.wyvern.client.modules.api.setting.impl.MultiBooleanSetting;
import wtf.wyvern.client.modules.impl.misc.*;
import wtf.wyvern.client.modules.impl.render.*;
import wtf.wyvern.client.screens.menu.MenuScreen;
import wtf.wyvern.utility.interfaces.IMinecraft;
import wtf.wyvern.utility.render.particles.ParticleEngine;

public final class ModuleManager implements IMinecraft {
    private final List<Module> modules = new ArrayList<>();
    private long lastKeyPressTime = 0;
    private int lastKeyCode = -1;
    private static final long DEBOUNCE_THRESHOLD_MS = 200;

    public ModuleManager() {
        init();
        EventManager.register(this);
    }

    private void init() {
        registerRender();
        registerMisc();
    }

    private void registerRender() {
        registerModule(Interface.INSTANCE);
        registerModule(AntiInvisible.INSTANCE);
        registerModule(NoRender.INSTANCE);
        registerModule(Predictions.INSTANCE);
        registerModule(SwingAnimation.INSTANCE);
        registerModule(Crosshair.INSTANCE);
        registerModule(ViewModel.INSTANCE);
        registerModule(Ambience.INSTANCE);
        registerModule(ShulkerPreview.INSTANCE);
        registerModule(FireworkESP.INSTANCE);
        registerModule(JumpCircle.INSTANCE);
        registerModule(Cosmetics.INSTANCE);
        registerModule(FullBright.INSTANCE);
        registerModule(Menu.INSTANCE);
        registerModule(EntityESP.INSTANCE);
        registerModule(Particles.INSTANCE);
        registerModule(TargetESP.INSTANCE);
        registerModule(KillEffect.INSTANCE);
        registerModule(BloomBlock.INSTANCE);
        registerModule(TotemPop.INSTANCE);
        registerModule(LineGlyphes.INSTANCE);
        registerModule(Arrows.INSTANCE);
        registerModule(Cubes.INSTANCE);
        registerModule(HitMarker.INSTANCE);
        registerModule(new ClientBow());
        registerModule(ClientSounds.INSTANCE);
        registerModule(AspectRatio.INSTANCE);
        registerModule(ShaderHands.INSTANCE);
        registerModule(CustomHitBox.INSTANCE);
    }

    private void registerMisc() {
        registerModule(NameProtect.INSTANCE);
        registerModule(ScoreboardHealth.INSTANCE);
    }

    private void registerModule(Module module) {
        modules.add(module);
    }

    public Module getModule(String name) {
        return modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public Set<Module> getActiveModules() {
        Set<Module> active = new HashSet<>();
        for (Module module : modules) {
            if (module.isEnabled()) {
                active.add(module);
            }
        }
        return active;
    }

    @EventTarget
    public void onKey(EventKey event) {
        if (mc.currentScreen == null && event.getAction() == 1) {
            int keyCode = event.getKeyCode();
            long currentTime = System.currentTimeMillis();

            if (keyCode == lastKeyCode && (currentTime - lastKeyPressTime) < DEBOUNCE_THRESHOLD_MS) {
                return;
            }

            lastKeyCode = keyCode;
            lastKeyPressTime = currentTime;

            for (Module module : modules) {
                if (module.getKeyCode() == keyCode && module.getKeyCode() != -1) {
                    module.toggle();
                }

                for (Setting setting : module.getSettings()) {
                    if (setting instanceof BooleanSetting booleanSetting) {
                        if (booleanSetting.getKeyCode() == keyCode && booleanSetting.getKeyCode() != -1) {
                            booleanSetting.toggle();
                        }
                    }
                }
            }

            for (Macro macro : Wyvern.getInstance().getMacroManager().getItems()) {
                if (keyCode == macro.getBind()) {
                    mc.getNetworkHandler().sendChatMessage(macro.getText());
                }
            }
        }
    }

    @EventTarget
    public void onRender(EventHudRender e) {
        Wyvern.getInstance().getThemeManager().getCurrentTheme().getAnimation().update(1.0F);

        for (Module module : modules) {
            module.getAnimation().update(module.isEnabled());

            for (Setting setting : module.getSettings()) {
                if (setting instanceof BooleanSetting booleanSetting) {
                    booleanSetting.getAnimation().update(booleanSetting.isEnabled());
                } else if (setting instanceof ModeSetting modeSetting) {
                    for (ModeSetting.Value value : modeSetting.getValues()) {
                        value.getAnimation().update(value.isSelected());
                    }
                } else if (setting instanceof MultiBooleanSetting multiBooleanSetting) {
                    for (MultiBooleanSetting.Value value : multiBooleanSetting.getBooleanSettings()) {
                        value.getAnimation().update(value.isEnabled());
                    }
                }
            }
        }

        MenuScreen menuScreen = Wyvern.getInstance().getMenuScreen();
        if (menuScreen.needToClose) {
            if (menuScreen.savedRunnable != null) {
                menuScreen.savedRunnable.run();
            }

            if (menuScreen.openAnimationMetanoise.getValue() <= 0.27F) {
                menuScreen.savedRunnable = null;
                menuScreen.needToClose = false;
                menuScreen.openAnimationMetanoise.setValue(0.0F);
                menuScreen.openAnimationMetanoise.setStartValue(0.0F);
            }
        }
    }

    @EventTarget
    private void onPacket(EventPacket e) {
        if (e.getPacket() instanceof CloseScreenS2CPacket && mc.currentScreen instanceof MenuScreen) {
            e.cancel();
        }
    }

    public List<Module> getModules() {
        return modules;
    }

}