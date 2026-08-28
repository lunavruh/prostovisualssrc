package dev.prostovisuals.modules.api;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.client.util.notify.Notify;
import dev.prostovisuals.client.util.notify.NotifyIcons;
import dev.prostovisuals.client.managers.HolyWorldFeatureControlManager;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.resource.language.I18n;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class Module implements Wrapper {
    private final String name, description;
    private final Category category;
    protected boolean toggled;
    @Setter private Bind bind = new Bind(-1, false);
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean serverControlledTransition;

    public Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }

    // Temporary backward compatibility; prefer using the 3-arg ctor with explicit description
    public Module(String name, Category category) {
        this(name, category, name);
    }

    public void onEnable() {
        toggled = true;
        prostovisuals.getInstance().getEventHandler().subscribe(this);
        if (!serverControlledTransition && !fullNullCheck() && !name.equals("UI")) {
            String translatedName = I18n.translate(name);
            String msg = I18n.translate("notify.featureEnabled", translatedName);
            prostovisuals.getInstance().getNotifyManager().add(new Notify(NotifyIcons.successIcon, msg, 1000));
        }
    }

    public void onDisable() {
        toggled = false;
        prostovisuals.getInstance().getEventHandler().unsubscribe(this);
        if (!serverControlledTransition && !fullNullCheck() && !name.equals("UI")) {
            String translatedName = I18n.translate(name);
            String msg = I18n.translate("notify.featureDisabled", translatedName);
            prostovisuals.getInstance().getNotifyManager().add(new Notify(NotifyIcons.failIcon, msg, 1000));
        }
    }

    public void setToggled(boolean toggled) {
        // A server-blocked feature must not be re-enabled through a bind, config or other code path.
        if (toggled && !serverControlledTransition && HolyWorldFeatureControlManager.getInstance().isBlocked(this)) {
            return;
        }
        // Avoid duplicate Orbit subscriptions and redundant autosave work.
        if (this.toggled == toggled) return;
        if (toggled) onEnable();
        else onDisable();
        // Server-enforced transitions are runtime-only and must not overwrite the user's config.
        if (!serverControlledTransition) {
            try {
                dev.prostovisuals.client.managers.AutoSaveManager asm = prostovisuals.getInstance().getAutoSaveManager();
                if (asm != null) asm.scheduleAutoSave();
            } catch (Throwable ignored) {}
        }
    }

    /** Runtime-only transition used by LiteAPI. Keeps config and user notifications untouched. */
    public void setToggledFromServer(boolean toggled) {
        boolean previous = serverControlledTransition;
        serverControlledTransition = true;
        try {
            setToggled(toggled);
        } finally {
            serverControlledTransition = previous;
        }
    }

    public void toggle() {
        setToggled(!toggled);
    }

    public static boolean fullNullCheck() {
        return mc.player == null || mc.world == null;
    }
}
