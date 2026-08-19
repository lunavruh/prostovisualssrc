package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventKey;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.spatial.SpatialDisplayManager;
import dev.prostovisuals.client.spatial.CaptureSourceRegistry;
import dev.prostovisuals.client.ui.spatial.SpatialDisplayScreen;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.modules.settings.impl.BindSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

public final class SpatialDisplay extends Module {
    private final BindSetting managerKey = new BindSetting("Manager Key", new Bind(GLFW.GLFW_KEY_F10, false));
    private final NumberSetting captureFps = new NumberSetting("Capture FPS", 120f, 30f, 120f, 1f);
    private final NumberSetting opacity = new NumberSetting("Opacity", 1f, 0.25f, 1f, 0.05f);

    public SpatialDisplay() {
        super("SpatialDisplay", Category.Render, "Vision Pro style curved displays in the Minecraft world");
        getSettings().add(managerKey);
        getSettings().add(captureFps);
        getSettings().add(opacity);
    }


    @Override
    public void onEnable() {
        super.onEnable();
        // Warm the source cache as soon as the module is enabled so F10 opens without an OS scan stall.
        CaptureSourceRegistry.warmup();
    }

    @EventHandler
    public void onRender(EventRender3D.Game e) {
        if (!isToggled() || mc.player == null || mc.world == null) return;
        SpatialDisplayManager manager = SpatialDisplayManager.getInstance();
        manager.tickCapture(captureFps.getValue().intValue());
        manager.render(e.getMatrices(), opacity.getValue());
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (!isToggled() || e.getAction() != GLFW.GLFW_PRESS || mc.currentScreen != null) return;
        Bind bind = managerKey.getValue();
        if (!bind.isMouse() && e.getKey() == bind.getKey()) mc.setScreen(new SpatialDisplayScreen(null));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        SpatialDisplayManager.getInstance().clear();
    }
}
