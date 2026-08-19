package dev.prostovisuals.client.ui.hud.impl;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.util.Network.Server;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.animations.infinity.InfinityAnimation;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.impl.utility.NameProtect;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compact Liquid Glass TargetHUD.
 * Left: large target head almost edge-to-edge.
 * Right: name, HP number + equipped armor row, HP bar.
 */
public class TargetHud extends HudElement implements ThemeManager.ThemeChangeListener {

    private final ThemeManager themeManager;
    private Color textColor;
    private Color absorbColor;

    private final BooleanSetting displayAbsorption = new BooleanSetting("displayAbsorption", true);

    private final InfinityAnimation fadeAnimation = new InfinityAnimation(Easing.OUT_QUAD);
    private final InfinityAnimation scaleAnimation = new InfinityAnimation(Easing.OUT_QUAD);
    private final InfinityAnimation slideAnimation = new InfinityAnimation(Easing.OUT_QUAD);
    private final InfinityAnimation hpAnimPx = new InfinityAnimation(Easing.BOTH_SINE);
    private final InfinityAnimation absAnimPx = new InfinityAnimation(Easing.BOTH_SINE);

    private LivingEntity lastTarget;
    private long lastSeenTime;
    private boolean forceFade;
    private Vec3d lastKnownCenter;

    private float animationDirectionX = 1f;
    private float animationDirectionY = 1f;

    private static final long HUD_DURATION = 2000L;
    private static final float WIDTH = 154f;
    private static final float HEIGHT = 46f;
    private static final float ROUNDING = 10f;
    private static final float PAD = 5f;

    public TargetHud() {
        super("TargetHud");
        themeManager = ThemeManager.getInstance();
        applyTheme(themeManager.getCurrentTheme());
        themeManager.addThemeChangeListener(this);
        getSettings().add(displayAbsorption);
    }

    private void applyTheme(ThemeManager.Theme theme) {
        Color c = theme.getAccentColor();
        textColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
        absorbColor = new Color(255, 190, 0, 255);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) {
        applyTheme(theme);
    }

    @Override
    public void onDisable() {
        themeManager.removeThemeChangeListener(this);
        super.onDisable();
    }

    private Vec3d entityCenter(LivingEntity entity, EventRender2D e) {
        Vec3d pos = entity.getLerpedPos(e.getTickDelta());
        return pos.add(0.0, entity.getHeight() * 0.5, 0.0);
    }

    private static boolean invisible(LivingEntity entity) {
        return entity.hasStatusEffect(StatusEffects.INVISIBILITY) || entity.isInvisible();
    }

    private boolean occluded(Vec3d from, Vec3d to) {
        HitResult hit = mc.world.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return hit.getType() != HitResult.Type.MISS;
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;

        long now = System.currentTimeMillis();

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult hit = (EntityHitResult) mc.crosshairTarget;
            if (hit.getEntity() instanceof LivingEntity living && living.isAlive()) {
                Vec3d center = entityCenter(living, e);
                if (!invisible(living) && !occluded(mc.player.getCameraPosVec(e.getTickDelta()), center)) {
                    if (lastTarget == null) {
                        animationDirectionX = (float) (Math.random() * 2.0 - 1.0);
                        animationDirectionY = (float) (Math.random() * 2.0 - 1.0);
                    }
                    lastTarget = living;
                    lastSeenTime = now;
                    forceFade = false;
                    lastKnownCenter = center;
                } else if (occluded(mc.player.getCameraPosVec(e.getTickDelta()), center)) {
                    forceFade = true;
                    lastKnownCenter = center;
                }
            }
        }

        if (lastTarget != null && (!lastTarget.isAlive() || now - lastSeenTime > HUD_DURATION)) {
            forceFade = true;
        }

        boolean previewMode = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen && lastTarget == null;
        LivingEntity display = previewMode ? mc.player : lastTarget;

        boolean show = display != null && !forceFade || previewMode;
        fadeAnimation.animate(show ? 1f : 0f, show ? 300 : 620);
        scaleAnimation.animate(show ? 1f : 0.985f, show ? 340 : 680);
        slideAnimation.animate(show ? 0f : 1f, show ? 360 : 720);

        if (fadeAnimation.getValue() <= 0.001f) {
            if (forceFade) {
                lastTarget = null;
                forceFade = false;
                lastKnownCenter = null;
            }
            return;
        }

        if (display == null) return;

        float x = getX();
        float y = getY();
        float hudScale = getHudScale();
        setBounds(x, y, WIDTH * hudScale, HEIGHT * hudScale);

        float fade = MathHelper.clamp(fadeAnimation.getValue(), 0f, 1f);
        int alpha = (int) (255f * fade);
        float rawHp = MathUtils.round(Server.getHealth(display, false));
        float maxHp = Math.max(1f, MathUtils.round(display.getMaxHealth()));
        float absorption = Math.max(0f, MathUtils.round(display.getAbsorptionAmount()));
        float hp01 = MathHelper.clamp(rawHp / maxHp, 0f, 1f);
        float abs01 = MathHelper.clamp(absorption / maxHp, 0f, 1f);

        float scale = scaleAnimation.getValue() * toggledAnimation.getValue() * fade;
        float slideX = 6f * slideAnimation.getValue() * animationDirectionX;
        float slideY = 5f * slideAnimation.getValue() * animationDirectionY;

        var matrices = e.getContext().getMatrices();
        matrices.push();
        matrices.translate(x, y, 0f);
        matrices.scale(hudScale, hudScale, 1f);
        matrices.translate(-x, -y, 0f);
        matrices.translate(x + WIDTH * 0.5f + slideX, y + HEIGHT * 0.5f + slideY, 0f);
        matrices.scale(scale, scale, 1f);
        matrices.translate(-(x + WIDTH * 0.5f), -(y + HEIGHT * 0.5f), 0f);

        float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
        LiquidGlassUtil.drawLiquidGlass(
                e.getContext(), x, y, WIDTH, HEIGHT, liquidTime,
                5.2f, 0.15f, 0.00155f, ROUNDING, 0.54f, 1.035f
        );

        // Head almost touches the glass bounds on the left.
        float headSize = HEIGHT - 2f;
        float headX = x + 1f;
        float headY = y + 1f;

        if (display instanceof PlayerEntity player) {
            Render2D.drawTexture(
                    matrices,
                    headX, headY,
                    headSize, headSize,
                    9f,
                    0.125f, 0.125f, 0.125f, 0.125f,
                    ((AbstractClientPlayerEntity) player).getSkinTextures().texture(),
                    new Color(255, 255, 255, alpha)
            );
        } else {
            Render2D.drawRoundedRect(matrices, headX, headY, headSize, headSize, 9f,
                    new Color(255, 255, 255, Math.min(alpha, 28)));
            Render2D.drawFont(matrices, Fonts.BOLD.getFont(14f), "?",
                    headX + headSize * 0.5f - Fonts.BOLD.getWidth("?", 14f) * 0.5f,
                    headY + headSize * 0.5f - Fonts.BOLD.getHeight(14f) * 0.5f,
                    new Color(255, 255, 255, alpha));
        }

        float contentX = headX + headSize + 7f;
        float contentRight = x + WIDTH - PAD;
        float contentW = contentRight - contentX;

        String name = display.getName().getString();
        if (previewMode) {
            NameProtect np = NameProtect.getInstance();
            if (np != null && np.isToggled()) {
                String replacement = np.getCustomName().getValue();
                name = replacement != null && !replacement.isEmpty() ? replacement : "Protected";
            }
        }
        name = ellipsize(name, 8.5f, contentW);
        Render2D.drawFont(matrices, Fonts.BOLD.getFont(8.5f), name,
                contentX, y + 5f,
                new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), alpha));

        // HP bar at the bottom-right section.
        float barX = contentX;
        float barY = y + HEIGHT - 8f;
        float barW = contentW;
        float barH = 5.5f;

        // Compact stats row sits directly above the HP bar.
        // HP stays aligned to the left edge; armor is larger and right-aligned.
        String hpText = formatHealth(rawHp);
        float statsY = barY - 10.2f;
        Render2D.drawFont(matrices, Fonts.BOLD.getFont(9.6f), hpText,
                contentX, statsY,
                new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), alpha));

        renderArmorRightAligned(
                e,
                display,
                contentRight,
                barY - 14.0f,
                contentX + Fonts.BOLD.getWidth(hpText, 9.6f) + 6f,
                fade
        );

        Render2D.drawRoundedRect(matrices, barX, barY, barW, barH, 2.75f,
                new Color(255, 255, 255, Math.min(alpha, 28)));

        float hpPx = Math.max(0f, Math.min(barW, hpAnimPx.animate(barW * hp01, 105)));
        Color accent = themeManager.getCurrentTheme().getAccentColor();
        if (hpPx > 0.2f) {
            Render2D.drawRoundedRect(matrices, barX, barY, hpPx, barH,
                    Math.min(2.75f, hpPx * 0.5f),
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
        }

        if (displayAbsorption.getValue() && absorption > 0f) {
            float absPx = Math.max(0f, Math.min(barW, absAnimPx.animate(barW * abs01, 120)));
            float start = Math.min(barW, hpPx);
            float available = Math.max(0f, barW - start);
            float drawAbs = Math.min(absPx, available);
            if (drawAbs > 0.2f) {
                Render2D.drawRoundedRect(matrices, barX + start, barY, drawAbs, barH,
                        Math.min(2.75f, drawAbs * 0.5f),
                        new Color(absorbColor.getRed(), absorbColor.getGreen(), absorbColor.getBlue(), alpha));
            }
        }

        matrices.pop();
        super.onRender2D(e);
    }

    private void renderArmorRightAligned(
            EventRender2D e,
            LivingEntity entity,
            float right,
            float y,
            float minX,
            float fade
    ) {
        List<ItemStack> armor = new ArrayList<>();
        for (ItemStack stack : entity.getArmorItems()) {
            if (!stack.isEmpty()) armor.add(stack);
        }
        Collections.reverse(armor); // helmet -> chest -> leggings -> boots
        if (armor.isEmpty()) return;

        // Render every equipped piece. Step compresses slightly if needed instead
        // of dropping the last item (the old code could hide boots).
        float itemScale = 0.98f;
        float visualSize = 16f * itemScale;
        float available = Math.max(visualSize, right - minX);
        float preferredStep = 15.0f;
        float step = armor.size() <= 1
                ? 0f
                : Math.max(9.5f, Math.min(preferredStep, (available - visualSize) / (armor.size() - 1)));

        float totalWidth = visualSize + Math.max(0, armor.size() - 1) * step;
        float drawX = right - totalWidth; // hard right alignment

        for (ItemStack stack : armor) {
            var matrices = e.getContext().getMatrices();
            matrices.push();
            matrices.translate(drawX, y, 40f);
            matrices.scale(itemScale, itemScale, 1f);
            e.getContext().drawItem(stack, 0, 0);
            matrices.pop();
            drawX += step;
        }
    }

    private String formatHealth(float hp) {
        if (Math.abs(hp - Math.round(hp)) < 0.05f) return Integer.toString(Math.round(hp));
        return String.format(java.util.Locale.ROOT, "%.1f", hp);
    }

    private String ellipsize(String text, float fontSize, float maxWidth) {
        if (text == null) return "";
        if (Fonts.BOLD.getWidth(text, fontSize) <= maxWidth) return text;
        String suffix = "...";
        float suffixW = Fonts.BOLD.getWidth(suffix, fontSize);
        if (suffixW >= maxWidth) return "";
        String result = "";
        for (int i = 1; i <= text.length(); i++) {
            String candidate = text.substring(0, i) + suffix;
            if (Fonts.BOLD.getWidth(candidate, fontSize) > maxWidth) break;
            result = candidate;
        }
        return result;
    }
}
