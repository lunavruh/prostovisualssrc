package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.util.renderer.Render3D;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import dev.prostovisuals.client.util.perf.Perf;
import org.joml.Matrix4f;

public class Trails extends Module implements ThemeManager.ThemeChangeListener {

    private final NumberSetting length = new NumberSetting("setting.trails.length", 20, 5, 200, 1);
    private final VisualColorSettings visualColor = new VisualColorSettings();

    private record TrailPoint(Vec3d pos, long timeMs) {}

    private static final long DEFAULT_LIFETIME_MS = 500L;

    private final ThemeManager themeManager;
    private final Map<PlayerEntity, Deque<TrailPoint>> trails = new IdentityHashMap<>();

    public Trails() {
        super("Trails", Category.Render, I18n.translate("module.trails.description"));
        this.themeManager = ThemeManager.getInstance();
        themeManager.addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) { }

    @EventHandler
    public void onRender3D(EventRender3D.Game e) {
        if (fullNullCheck()) return;
        if (mc.options.getPerspective().isFirstPerson()) return;
        try (var __ = Perf.scopeCpu("Trails.onRender3D")) {
            Render3D.prepare();
            float tickDelta = Render3D.getTickDelta();
            // Получаем актуальный цвет темы (включая градиентные темы)
            Color base = visualColor.resolve();
            int baseR = base.getRed();
            int baseG = base.getGreen();
            int baseB = base.getBlue();
            int baseA = 255;
            int maxPoints = length.getValue().intValue();
            float edgeWidth = Math.max(0.5f, Math.min(6.0f, 0.5f * 0.4f));

            long now = System.currentTimeMillis();

            PlayerEntity p = mc.player;
            Deque<TrailPoint> q = trails.computeIfAbsent(p, k -> new ArrayDeque<>());

                // Remove expired points (fixed lifetime)
                while (!q.isEmpty() && now - q.peekFirst().timeMs > DEFAULT_LIFETIME_MS) q.removeFirst();

                double h = p.getEyeHeight(p.getPose());
                Vec3d origin = p.getLerpedPos(tickDelta);
            if (q.isEmpty() || q.getLast().pos.squaredDistanceTo(origin) > 0.0001) {
                q.addLast(new TrailPoint(origin, now));
                while (q.size() > maxPoints) q.removeFirst();
            }

            if (q.size() >= 2) {
                int sz = q.size();
                int rgb = (baseR << 16) | (baseG << 8) | baseB;
                Vec3d camera = mc.gameRenderer.getCamera().getPos();
                Matrix4f identity = new net.minecraft.client.util.math.MatrixStack()
                        .peek().getPositionMatrix();
                Iterator<TrailPoint> iterator = q.iterator();
                TrailPoint a = iterator.next();
                int index = 0;
                while (iterator.hasNext()) {
                    TrailPoint b = iterator.next();
                    float tA = (float) index / (float) (sz - 1);
                    float tB = (float) (index + 1) / (float) (sz - 1);
                    int ageA = (int) Math.min(255, Math.max(0, baseA * tA));
                    int ageB = (int) Math.min(255, Math.max(0, baseA * tB));
                    int colorA = (ageA << 24) | rgb;
                    int colorB = (ageB << 24) | rgb;

                    addQuadVertical(a.pos, b.pos, h, camera, identity, colorA, colorB);
                    Render3D.drawLine(a.pos, b.pos, colorA, edgeWidth);
                    Render3D.drawLine(a.pos.add(0, h, 0), b.pos.add(0, h, 0), colorA, edgeWidth);
                    a = b;
                    index++;
                }
            }
            Render3D.render();
        }
    }

    private void addQuadVertical(Vec3d aBottom, Vec3d bBottom, double height,
                                 Vec3d camera, Matrix4f matrix, int colorA, int colorB) {
        float ax = (float) (aBottom.x - camera.x);
        float ay = (float) (aBottom.y - camera.y);
        float az = (float) (aBottom.z - camera.z);
        float bx = (float) (bBottom.x - camera.x);
        float by = (float) (bBottom.y - camera.y);
        float bz = (float) (bBottom.z - camera.z);
        Render3D.Vertex[] vertices = {
                new Render3D.Vertex(matrix, ax, ay, az, colorA),
                new Render3D.Vertex(matrix, ax, ay + (float) height, az, colorA),
                new Render3D.Vertex(matrix, bx, by + (float) height, bz, colorB),
                new Render3D.Vertex(matrix, bx, by, bz, colorB)
        };
        Render3D.QUADS.add(new Render3D.VertexCollection(vertices));
    }

    @Override
    public void onDisable() {
        trails.clear();
        super.onDisable();
    }
}
