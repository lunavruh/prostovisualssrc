package dev.prostovisuals.client.managers;

import dev.prostovisuals.client.ui.hud.impl.ArmorHUD;
import dev.prostovisuals.client.ui.hud.impl.Potions;
import dev.prostovisuals.client.ui.hud.impl.ScoreboardHUD;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.client.events.impl.EventMouse;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.ui.hud.impl.TargetHud;
import dev.prostovisuals.client.ui.hud.impl.HotbarHUD;
import dev.prostovisuals.client.ui.hud.impl.Watermark;
import dev.prostovisuals.client.ui.hud.windows.Window;
import dev.prostovisuals.client.util.render.Wrapper;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import dev.prostovisuals.prostovisuals;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static dev.prostovisuals.client.util.Wrapper.mc;

@Getter
public class HudManager implements Wrapper {

    @Setter private HudElement currentDragging;
    private final List<HudElement> hudElements = new ArrayList<>();
    protected final ListSetting elements = new ListSetting("HUD Elements",
            new BooleanSetting("Watermark", true),
            new BooleanSetting("Potions", true),
            new BooleanSetting("Notifications", true),
            new BooleanSetting("Information", true),
            new BooleanSetting("Keybinds HUD", true),
            new BooleanSetting("Target HUD", true),
            new BooleanSetting("Module List", true)
    );
    @Setter private Window window;

    public HudManager() {
        // Wyvern remains the only HUD renderer; this manager only provides the old
        // RMB-on-empty-chat-screen element picker and forwards toggles into Wyvern HUD.
        prostovisuals.getInstance().getEventHandler().subscribe(this);
        // Do NOT touch Interface.INSTANCE here. ProstoVisual's ModInitializer runs
        // before Wyvern's ClientModInitializer, and Interface's constructor needs
        // Wyvern managers (NotifyManager, etc.) to already exist. Sync lazily later.
    }

    private boolean isWyvernHudReady() {
        try {
            var wyvern = wtf.wyvern.Wyvern.getInstance();
            return wyvern.getModuleManager() != null && wyvern.getNotifyManager() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void syncFromWyvern() {
        if (!isWyvernHudReady()) return;
        try {
            var nativeHud = wtf.wyvern.client.modules.impl.render.Interface.INSTANCE;
            for (int i = 0; i < elements.getValue().size() && i < nativeHud.getHudElementCount(); i++) {
                Setting<?> setting = elements.getValue().get(i);
                if (setting instanceof BooleanSetting b) b.setValue(nativeHud.isHudElementEnabled(i));
            }
        } catch (Throwable ignored) {}
    }

    private void syncToWyvern() {
        if (!isWyvernHudReady()) return;
        try {
            var nativeHud = wtf.wyvern.client.modules.impl.render.Interface.INSTANCE;
            boolean anyEnabled = false;
            for (int i = 0; i < elements.getValue().size() && i < nativeHud.getHudElementCount(); i++) {
                Setting<?> setting = elements.getValue().get(i);
                if (setting instanceof BooleanSetting b) {
                    nativeHud.setHudElementEnabled(i, b.getValue());
                    anyEnabled |= b.getValue();
                }
            }
            // The old picker used to flip child flags while the parent Interface module stayed off,
            // which made the menu look functional but rendered nothing.
            if (anyEnabled && !nativeHud.isEnabled()) nativeHud.setToggled(true);
        } catch (Throwable ignored) {}
    }

    private void saveWyvernHud() {
        try {
            var wyvern = wtf.wyvern.Wyvern.getInstance();
            if (wyvern != null && wyvern.getConfigManager() != null) wyvern.getConfigManager().saveConfig("current_config");
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (Module.fullNullCheck()) return;

        if (window != null) {
            if (!(mc.currentScreen instanceof ChatScreen)) window.reset();

            if (window.closed()) {
                window = null;
                return;
            }

            window.render(e.getContext(), mouseX(), mouseY());
            syncToWyvern();
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (!(mc.currentScreen instanceof ChatScreen) || Module.fullNullCheck()) return;

        if (e.getAction() == 1) {
            if (window != null) {
                if (MathUtils.isHovered(window.getX(), window.getY(), window.getWidth(), window.getFinalHeight(), mouseX(), mouseY())) {
                    window.mouseClicked(mouseX(), mouseY(), e.getButton());
                    // Apply the picker state immediately. Waiting for the next HUD render made
                    // the old RMB menu look clickable while Wyvern kept the previous values.
                    syncToWyvern();
                    saveWyvernHud();
                    return;
                } else window.reset();
            }

            if (e.getButton() == 1) {
                // Empty-space RMB menu controls the real Wyvern HUD. Individual Wyvern HUD
                // elements still keep their own LMB dragging logic.
                syncFromWyvern();
                window = new Window(mouseX() + 3, mouseY() + 3, 112, 12.5f, List.of(elements));
            }
        }
    }

    public int mouseX() {
        return (int) (mc.mouse.getX() / mc.getWindow().getScaleFactor());
    }

    public int mouseY() {
        return (int) (mc.mouse.getY() / mc.getWindow().getScaleFactor());
    }

    private void addElements(HudElement... element) {
        this.hudElements.addAll(List.of(element));
    }
}