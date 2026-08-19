package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.client.util.renderer.Render3D;
import dev.prostovisuals.client.managers.ThemeManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WorldParticles extends Module implements ThemeManager.ThemeChangeListener {

    private final NumberSetting particleSize = new NumberSetting("Размер", 0.15f, 0.05f, 0.20f, 0.01f);
    private final NumberSetting maxParticles = new NumberSetting("Количество", 150f, 20f, 500f, 5f);
    private final NumberSetting spawnInterval = new NumberSetting("Интервал спавна", 60f, 10f, 200f, 10f);
    private final float spawnRadius = 10f;

    private final ListSetting mode = new ListSetting(
            "Режим",
            true,
            new BooleanSetting("Простой", true),
            new BooleanSetting("Взлет", false)
    );

    private final BooleanSetting randomColor = new BooleanSetting("Рандомный цвет", false);
    private final BooleanSetting glossy = new BooleanSetting("Глянцевые", false);

    private final EnumSetting<ParticleStyle> particleStyle = new EnumSetting<>("Particle Style", ParticleStyle.GLOW);

    private Identifier getParticleTexture() {
        return switch (particleStyle.getValue()) {
            case STAR -> prostovisuals.id("hud/kimiko_star.png");
            case SNOWFLAKE -> prostovisuals.id("hud/kimiko_snowflake.png");
            case HEART -> prostovisuals.id("hud/kimiko_heart.png");
            case CROWN -> prostovisuals.id("hud/kimiko_crown.png");
            case LIGHTNING -> prostovisuals.id("hud/kimiko_lightning.png");
            case GLOW -> prostovisuals.id("hud/glow.png");
        };
    }

    private final ThemeManager themeManager;
    private Color currentColor;
    private final List<Particle> particles = new ArrayList<>();
    private long lastSpawnTime = 0;
    private static final long SIM_STEP_NS = 16_666_667L; // 60 Hz simulation
    private long lastSimulationNs = 0L;
    private long simulationAccumulatorNs = 0L;

    public WorldParticles() {
        super("WorldParticles", Category.Render, "Kimiko-style world particles");
        this.themeManager = ThemeManager.getInstance();
        this.currentColor = themeManager.getThemeColor();
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game e) {
        if (fullNullCheck()) return;

        long now = System.currentTimeMillis();
        boolean vzletMode = isVzletMode();
        boolean glossyMode = glossy.getValue();

        if (now - lastSpawnTime >= spawnInterval.getValue()) {
            spawnParticle(vzletMode);
            lastSpawnTime = now;
        }

        // Particle physics is deliberately decoupled from rendered FPS.  The
        // previous code randomized and integrated every particle once per
        // rendered frame, making 180 FPS roughly three times more expensive
        // than 60 FPS.  Simulate at a stable 60 Hz and interpolate positions
        // when drawing, so motion stays smooth at high refresh rates.
        long nowNs = System.nanoTime();
        if (lastSimulationNs == 0L) lastSimulationNs = nowNs;
        long frameNs = Math.min(50_000_000L, Math.max(0L, nowNs - lastSimulationNs));
        lastSimulationNs = nowNs;
        simulationAccumulatorNs += frameNs;

        int steps = 0;
        while (simulationAccumulatorNs >= SIM_STEP_NS && steps++ < 3) {
            simulateParticles(vzletMode, now);
            simulationAccumulatorNs -= SIM_STEP_NS;
        }
        if (steps >= 3 && simulationAccumulatorNs >= SIM_STEP_NS) {
            simulationAccumulatorNs %= SIM_STEP_NS;
        }
        float interpolation = Math.min(1.0f, simulationAccumulatorNs / (float) SIM_STEP_NS);

        float lightFactor = Math.max(0.5f, 1.0f - mc.world.getBrightness(mc.player.getBlockPos()));

        Render3D.beginBillboardBatch(getParticleTexture());
        for (Particle p : particles) {
            // Particles only spawn within ten blocks of the player. Creating a
            // temporary Box for a frustum test for every particle every frame
            // cost more CPU/GC than simply appending these few billboard verts.
            renderParticle(p, e, now, vzletMode, glossyMode, lightFactor, interpolation);
        }
        Render3D.endBillboardBatch();
    }

    private void spawnParticle(boolean vzletMode) {
        ensureSpace();

        double radius = spawnRadius;
        Vec3d pos;
        Vec3d vel;

        if (vzletMode) {
            pos = mc.player.getPos().add(
                    ThreadLocalRandom.current().nextDouble(-radius, radius),
                    0.2, // чуть выше ног
                    ThreadLocalRandom.current().nextDouble(-radius, radius)
            );
            vel = new Vec3d(
                    ThreadLocalRandom.current().nextDouble(-0.01, 0.01),
                    0.03,
                    ThreadLocalRandom.current().nextDouble(-0.01, 0.01)
            );
        } else {
            pos = mc.player.getPos().add(
                    ThreadLocalRandom.current().nextDouble(-radius, radius),
                    ThreadLocalRandom.current().nextDouble(5, 8),
                    ThreadLocalRandom.current().nextDouble(-radius, radius)
            );
            vel = new Vec3d(
                    ThreadLocalRandom.current().nextDouble(-0.01, 0.01),
                    -0.01,
                    ThreadLocalRandom.current().nextDouble(-0.01, 0.01)
            );
        }

        // Live theme color so gradient themes animate; random overrides if enabled
        Color color = randomColor.getValue()
                ? new Color(ThreadLocalRandom.current().nextInt(256),
                ThreadLocalRandom.current().nextInt(256),
                ThreadLocalRandom.current().nextInt(256))
                : currentColor;

        particles.add(new Particle(pos, vel, 5000, color));
    }

    private void ensureSpace() {
        int limit = maxParticles.getValue().intValue();
        while (particles.size() >= limit && !particles.isEmpty()) {
            particles.remove(0);
        }
    }

    private void simulateParticles(boolean vzletMode, long now) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            if (now - particle.spawnTime > particle.lifeTime) {
                particles.remove(i);
                continue;
            }
            particle.updateMotion(vzletMode, now);
            if (particle.y < -2.0) particles.remove(i);
        }
    }

    private void renderParticle(Particle p, EventRender3D.Game e, long now,
                                boolean vzletMode, boolean glossyMode, float lightFactor,
                                float interpolation) {
        float baseSize = particleSize.getValue();
        float life = p.getLifeProgress(now);

        float sizeFactor;
        if (life < 0.2f) sizeFactor = life / 0.2f;
        else if (life > 0.8f) sizeFactor = (1 - life) / 0.2f;
        else sizeFactor = 1f;

        float heightFactor = 1f;
        if (!vzletMode && p.y < 1.5) heightFactor = (float) (p.y / 1.5);

        float size = baseSize * sizeFactor * heightFactor;
        int alpha = Math.max(0, Math.min(255, (int) (255 * sizeFactor * heightFactor * lightFactor)));

        // Цвет всегда насыщенный, не зависит от lightFactor
        int rgb = p.color.getRGB() & 0x00FFFFFF;
        int rgbaMain = (alpha << 24) | rgb;
        double renderX = p.prevX + (p.x - p.prevX) * interpolation;
        double renderY = p.prevY + (p.y - p.prevY) * interpolation;
        double renderZ = p.prevZ + (p.z - p.prevZ) * interpolation;

        if (glossyMode) {
            // Glossy: brighter, larger glow
            Render3D.batchBillboardLayers(e.getMatrices(), renderX, renderY, renderZ,
                    size * 2.5f, rgbaMain,
                    size * 1.5f, rgbaMain,
                    0f, 0, 2);
        } else {
            // Non-glossy: softer outer with reduced alpha, mid core, small white highlight
            int softAlpha = Math.max(0, Math.min(255, (int) (alpha * 0.65f)));
            int rgbaSoft = (softAlpha << 24) | rgb;
            int whiteAlpha = Math.min(255, alpha + 50);
            int rgbaWhite = (whiteAlpha << 24) | 0x00FFFFFF;
            Render3D.batchBillboardLayers(e.getMatrices(), renderX, renderY, renderZ,
                    size * 2.2f, rgbaSoft,
                    size * 1.2f, rgbaMain,
                    size * 0.5f, rgbaWhite, 3);
        }
    }

    private boolean isVzletMode() {
        BooleanSetting vzlet = mode.getName("Взлет");
        return vzlet != null && vzlet.getValue();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        currentColor = themeManager.getThemeColor();
        themeManager.addThemeChangeListener(this);
        lastSimulationNs = 0L;
        simulationAccumulatorNs = 0L;
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) {
        this.currentColor = theme.getBackgroundColor();
        // Обновляем цвет у всех существующих частиц
        for (Particle p : particles) {
            p.color = this.currentColor;
        }
    }

    @Override
    public void onDisable() {
        particles.clear();
        lastSimulationNs = 0L;
        simulationAccumulatorNs = 0L;
        themeManager.removeThemeChangeListener(this);
        super.onDisable();
    }

    public enum ParticleStyle implements Nameable {
        GLOW("Glow"), STAR("Star"), SNOWFLAKE("Snowflake"), HEART("Heart"), CROWN("Crown"), LIGHTNING("Lightning");
        private final String name;
        ParticleStyle(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }

    private class Particle {
        double x, y, z;
        double prevX, prevY, prevZ;
        double velocityX, velocityY, velocityZ;
        long spawnTime;
        long lifeTime;
        Color color;

        Particle(Vec3d pos, Vec3d vel, long lifeTime, Color color) {
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.velocityX = vel.x;
            this.velocityY = vel.y;
            this.velocityZ = vel.z;
            this.spawnTime = System.currentTimeMillis();
            this.lifeTime = lifeTime;
            this.color = color;
        }

        void updateMotion(boolean vzlet, long now) {
            prevX = x;
            prevY = y;
            prevZ = z;

            double motionRandom = 0.002;

            ThreadLocalRandom random = ThreadLocalRandom.current();
            velocityX += (random.nextDouble() - 0.5) * motionRandom;
            velocityY += (random.nextDouble() - 0.5) * motionRandom * 0.5;
            velocityZ += (random.nextDouble() - 0.5) * motionRandom;

            velocityY = vzlet
                    ? 0.03
                    : -0.005 + Math.sin((now - spawnTime) / 500.0) * 0.002;

            x += velocityX;
            y += velocityY;
            z += velocityZ;
        }

        float getLifeProgress(long now) {
            return (float) (now - spawnTime) / lifeTime;
        }
    }
}
