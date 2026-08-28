package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.client.events.impl.EventThemeChanged;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.renderer.Render3D;
import dev.prostovisuals.client.util.perf.Perf;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;
import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.managers.ThemeManager;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.resource.language.I18n;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class JumpCircle extends Module implements ThemeManager.ThemeChangeListener {

    // 👇 сильно уменьшаем размер
    private final NumberSetting size = new NumberSetting("Circle Size", 0.50f, 0.15f, 2.0f, 0.05f);
    // Base size and expansion distance are intentionally separate controls.
    private final NumberSetting range = new NumberSetting("Circle Range", 0.75f, 0.0f, 3.0f, 0.05f);
    private final NumberSetting Life_Time = new NumberSetting("Life Time", 0.5f, 0.2f, 2.0f, 0.05f);
    private final NumberSetting speed = new NumberSetting("Speed", 1.0f, 0.5f, 3.0f, 0.1f);

    private static final long GROW_MS = 600L;

    // Текстуры: вариант 1 — jumpcircle.png, вариант 2 — circle.png
    private final BooleanSetting texJumpCircle = new BooleanSetting("First", true, () -> false);
    private final BooleanSetting texCircle = new BooleanSetting("Second", false, () -> false);
    private final ListSetting textureMode = new ListSetting("Texture", true, texJumpCircle, texCircle);

    // Анимации: Grow, Pulse, Ripple
    private final BooleanSetting animGrow = new BooleanSetting("Grow", true, () -> false);
    private final BooleanSetting animPulse = new BooleanSetting("Pulse", false, () -> false);
    private final BooleanSetting animRipple = new BooleanSetting("Ripple", false, () -> false);
    private final ListSetting animationMode = new ListSetting("Animation", true, animGrow, animPulse, animRipple);
    private final VisualColorSettings visualColor = new VisualColorSettings();

    private final Identifier texJump = prostovisuals.id("textures/jumpcircle.png");
    private final Identifier texCircle2 = prostovisuals.id("textures/circle.png");

    private final List<Circle> circles = new CopyOnWriteArrayList<>();
    private final ThemeManager themeManager;
    private Color currentColor;

    private boolean wasOnGround = true;
    private long lastJumpTime = 0;

    public JumpCircle() {
        super("JumpCircle", Category.Render, I18n.translate("module.jumpcircle.description"));
        this.themeManager = ThemeManager.getInstance();
        this.currentColor = themeManager.getThemeColor();
        themeManager.addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) {
        this.currentColor = theme.getBackgroundColor();
    }

    @EventHandler
    public void onThemeChanged(EventThemeChanged event) {
        this.currentColor = event.getTheme().getBackgroundColor();
    }

    @Override
    public void onDisable() {
        themeManager.removeThemeChangeListener(this);
        circles.clear();
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick e) {
        if (fullNullCheck()) return;
        ClientPlayerEntity p = mc.player;
        if (p == null) return;

        boolean onGround = p.isOnGround();
        long currentTime = System.currentTimeMillis();
        double velocityY = p.getVelocity().y;

        // Упрощенная логика: игрок был на земле, теперь не на земле, и есть положительная скорость
        if (wasOnGround && !onGround && velocityY > 0.01 && (currentTime - lastJumpTime) > 50) {
            // Создаем круг на позиции прыжка, но на 1 блок выше
            BlockPos blockPos = p.getBlockPos();
            double y = blockPos.getY() + 0.1; // На 1 блок выше + небольшой отступ
            Vec3d origin = new Vec3d(p.getX(), y, p.getZ());
            circles.add(new Circle(origin, (long) (Life_Time.getValue() * 1000L)));
            lastJumpTime = currentTime;
        }

        wasOnGround = onGround;
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game e) {
        if (fullNullCheck()) return;
        try (var __ = Perf.scopeCpu("JumpCircle.onRender3D")) {
            long now = System.currentTimeMillis();
            for (int i = circles.size() - 1; i >= 0; i--) {
                Circle c = circles.get(i);
                if (now - c.spawnTime > c.ttl) circles.remove(i);
            }

            for (Circle c : circles) {
                long age = now - c.spawnTime;

                // Нормированная величина времени жизни 0..1 с учетом скорости
                float t = Math.max(0f, Math.min(1f, (age / (float) c.ttl) * speed.getValue()));

                // Вычисляем масштаб и прозрачность в зависимости от режима
                float radiusMul;
                float alphaK;
                if (animPulse.getValue()) {
                    // Пульсация: несколько «всплесков» в течение жизни
                    float pulses = 2.0f; // количество пульсов
                    float s = (float) Math.sin(Math.PI * pulses * t);
                    radiusMul = 0.7f + 0.5f * Math.max(0f, s);
                    alphaK = Math.max(0f, s) * (1.0f - t);
                } else if (animRipple.getValue()) {
                    // Ripple: мягкое появление/исчезновение и ease-out по радиусу
                    float te = Easing.EASE_OUT_CIRC.apply(Math.max(0f, Math.min(1f, t)));
                    radiusMul = 0.6f + 0.8f * te;
                    alphaK = (float) Math.pow(Math.sin(Math.max(0f, Math.min(1f, t)) * (float) Math.PI), 1.2f);
                } else {
                    // Grow: используем плавный S-curve по времени + эксп. затухание
                    float te = Easing.BOTH_SINE.apply(t);
                    radiusMul = 0.6f + 0.4f * te;
                    alphaK = (float) Math.pow(1.0f - t, 0.8f) * te;
                }

                // Dedicated fade envelope: no popping at spawn or despawn.
                float fadeIn = smoothstep01(Math.min(1f, t / 0.14f));
                float fadeOut = smoothstep01(Math.min(1f, (1f - t) / 0.30f));
                alphaK = Math.max(0f, Math.min(1f, alphaK * fadeIn * fadeOut));
                int alpha = (int) (255 * alphaK);
                if (alpha <= 2) continue;

                // Circle Size = initial/base radius. Circle Range = how far it expands.
                float progress = Easing.BOTH_SINE.apply(Math.max(0f, Math.min(1f, t)));
                float r = Math.max(0.03f, size.getValue() + range.getValue() * progress * radiusMul);
                // Live theme color each frame for gradient themes
                Color themeColor = visualColor.resolve();
                Color drawColor = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), alpha);

                MatrixStack matrices = e.getMatrices();
                matrices.push();
                matrices.translate(
                        c.origin.x - mc.getEntityRenderDispatcher().camera.getPos().x,
                        c.origin.y - mc.getEntityRenderDispatcher().camera.getPos().y,
                        c.origin.z - mc.getEntityRenderDispatcher().camera.getPos().z
                );

                Identifier texture = getSelectedTexture();
                Render3D.drawTextureVivid(
                        matrices,
                        -r, 0, -r,
                        r, 0, r,
                        texture,
                        drawColor,
                        1.0f
                );

                matrices.pop();
            }
        }
    }

    private Identifier getSelectedTexture() {
        if (texJumpCircle.getValue() && texCircle.getValue()) {
            texCircle.setValue(false);
        }
        if (!texJumpCircle.getValue() && !texCircle.getValue()) {
            texJumpCircle.setValue(true);
        }
        return texJumpCircle.getValue() ? texJump : texCircle2;
    }


    private static float smoothstep01(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static class Circle {
        final Vec3d origin;
        final long spawnTime;
        final long ttl;

        Circle(Vec3d origin, long ttl) {
            this.origin = origin;
            this.spawnTime = System.currentTimeMillis();
            this.ttl = ttl;
        }
    }
}
