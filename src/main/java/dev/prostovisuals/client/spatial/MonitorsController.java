package dev.prostovisuals.client.spatial;

import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.prostovisuals;
import meteordevelopment.orbit.EventHandler;

/** Always-on backend for the integrated ClickGUI Monitors page. */
public final class MonitorsController {
    private static final MonitorsController INSTANCE = new MonitorsController();
    private boolean enabled = true;
    private int captureFps = 120;
    private float opacity = 1.0f;

    private MonitorsController() {}
    public static MonitorsController getInstance() { return INSTANCE; }

    public void init() {
        prostovisuals.getInstance().getEventHandler().subscribe(this);
        CaptureSourceRegistry.warmup();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) CaptureSourceRegistry.warmup();
    }
    public int getCaptureFps() { return captureFps; }
    public void setCaptureFps(int fps) { captureFps = Math.max(30, Math.min(120, fps)); }
    public float getOpacity() { return opacity; }
    public void setOpacity(float value) { opacity = Math.max(0.25f, Math.min(1.0f, value)); }

    @EventHandler
    public void onRender(EventRender3D.Game e) {
        if (!enabled) return;
        SpatialDisplayManager manager = SpatialDisplayManager.getInstance();
        manager.tickCapture(captureFps);
        manager.render(e.getMatrices(), opacity);
    }
}
