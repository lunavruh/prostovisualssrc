package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.custommodels.CosmeticEntry;
import dev.prostovisuals.client.custommodels.FiguraCosmeticsEngine;
import dev.prostovisuals.client.events.impl.EventKey;
import dev.prostovisuals.client.events.impl.EventMouse;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.client.ui.custommodels.CustomModelsScreen;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.modules.settings.impl.BindSetting;
import dev.prostovisuals.modules.settings.impl.StringSetting;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Custom model/cosmetics hub. The picker stays in the dedicated ProstoVisual menu,
 * while Figura avatars keep their original .bbmodel geometry, Lua animations and physics underneath.
 */
public final class CustomModels extends Module {
    public static final String TRALALERO_MODEL = "Tralalero Tralala";
    public static final String FIGURA_PREFIX = "FIGURA:";

    private final BindSetting menuKey = new BindSetting("Models Menu", new Bind(GLFW.GLFW_KEY_F9, false));
    private final StringSetting selectedModel = new StringSetting("Selected Model", TRALALERO_MODEL, () -> false, false);

    private String lastApplied = "";
    private UUID lastPlayerUuid;
    private long nextRetryAt;

    public CustomModels() {
        super("CustomModels", Category.Render, "3D models and animated Figura cosmetics");
        getSettings().add(menuKey);
        getSettings().add(selectedModel);
    }

    public BindSetting getMenuKey() { return menuKey; }
    public String getSelectedModel() { return selectedModel.getValue(); }
    public boolean isTralaleroSelected() { return TRALALERO_MODEL.equalsIgnoreCase(getSelectedModel()); }
    public boolean isFiguraSelected() { return getSelectedModel() != null && getSelectedModel().startsWith(FIGURA_PREFIX); }

    public String getSelectedFiguraPath() {
        return isFiguraSelected() ? getSelectedModel().substring(FIGURA_PREFIX.length()) : "";
    }

    public void selectTralalero() {
        FiguraCosmeticsEngine.clearLocal();
        selectedModel.setValue(TRALALERO_MODEL);
        lastApplied = TRALALERO_MODEL;
        if (!isToggled()) setToggled(true);
    }

    public void selectCosmetic(CosmeticEntry entry) {
        if (entry == null) return;
        selectedModel.setValue(FIGURA_PREFIX + entry.relativePath());
        lastApplied = "";
        if (!isToggled()) setToggled(true);
        applyFiguraNow();
    }

    public void selectDefaultPlayer() {
        FiguraCosmeticsEngine.clearLocal();
        lastApplied = "";
        if (isToggled()) setToggled(false);
    }

    public boolean isSelected(CosmeticEntry entry) {
        return entry != null && isFiguraSelected() && entry.relativePath().equalsIgnoreCase(getSelectedFiguraPath());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        FiguraCosmeticsEngine.ensureInstalled();
        lastApplied = "";
        nextRetryAt = 0L;
    }

    @Override
    public void onDisable() {
        FiguraCosmeticsEngine.clearLocal();
        lastApplied = "";
        lastPlayerUuid = null;
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;
        UUID uuid = mc.player.getUuid();
        if (!uuid.equals(lastPlayerUuid)) {
            lastPlayerUuid = uuid;
            lastApplied = "";
        }

        if (isTralaleroSelected()) {
            if (!TRALALERO_MODEL.equals(lastApplied)) {
                FiguraCosmeticsEngine.clearLocal();
                lastApplied = TRALALERO_MODEL;
            }
            return;
        }

        if (isFiguraSelected() && !getSelectedModel().equals(lastApplied) && System.currentTimeMillis() >= nextRetryAt) {
            applyFiguraNow();
        }
    }

    private void applyFiguraNow() {
        if (mc.player == null || !isFiguraSelected()) return;
        CosmeticEntry entry = FiguraCosmeticsEngine.findByRelativePath(getSelectedFiguraPath());
        if (entry != null && FiguraCosmeticsEngine.applyLocal(entry)) {
            lastApplied = getSelectedModel();
        } else {
            nextRetryAt = System.currentTimeMillis() + 1500L;
        }
    }

    /** Called by ModuleManager even while this module is disabled. */
    public void handleGlobalMenuKey(EventKey e) {
        if (e.getAction() != GLFW.GLFW_PRESS || mc.currentScreen != null) return;
        Bind bind = menuKey.getValue();
        if (bind == null || bind.isMouse() || bind.getKey() < 0) return;
        if (e.getKey() == bind.getKey()) mc.setScreen(new CustomModelsScreen(null));
    }

    public void handleGlobalMenuMouse(EventMouse e) {
        if (e.getAction() != GLFW.GLFW_PRESS || mc.currentScreen != null) return;
        Bind bind = menuKey.getValue();
        if (bind == null || !bind.isMouse() || bind.getKey() < 0) return;
        if (e.getButton() == bind.getKey()) mc.setScreen(new CustomModelsScreen(null));
    }

    @EventHandler
    public void onKey(EventKey e) {
        // Dedicated menu key is handled globally by ModuleManager.
    }
}
