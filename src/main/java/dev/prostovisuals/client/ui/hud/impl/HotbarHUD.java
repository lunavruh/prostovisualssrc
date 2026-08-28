package dev.prostovisuals.client.ui.hud.impl;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.animations.infinity.InfinityAnimation;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;

import java.awt.Color;

public class HotbarHUD extends HudElement {

    private final ThemeManager themeManager;

    private static final float BAR_WIDTH = 180f;
    private static final float BAR_HEIGHT = 20f;
    private static final float SLOT_SIZE = 20f;
    private static final float RADIUS = 5.5f;

    private final InfinityAnimation selectedXAnimation = new InfinityAnimation(Easing.OUT_QUAD);
    private final InfinityAnimation selectedAlphaAnimation = new InfinityAnimation(Easing.BOTH_SINE);
    private float lastSelectedX = -1f;

    public HotbarHUD() {
        super("Hotbar");
        this.themeManager = ThemeManager.getInstance();
    }

    /** Applies the exact same move/scale to vanilla HP, hunger, armor and XP UI. */
    public void pushVanillaGroupTransform(net.minecraft.client.gui.DrawContext context) {
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float vanillaX = sw * 0.5f - BAR_WIDTH * 0.5f;
        float vanillaY = sh - 22.0f;
        float scale = getHudScale();
        context.getMatrices().push();
        context.getMatrices().translate(getX(), getY(), 0f);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-vanillaX, -vanillaY, 0f);
    }

    public void popVanillaGroupTransform(net.minecraft.client.gui.DrawContext context) {
        context.getMatrices().pop();
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;
        if (mc.player == null || mc.player.isSpectator()) return;

        var context = e.getContext();
        var matrices = context.getMatrices();

        float baseX = getX();
        float baseY = getY();
        float hudScale = getHudScale();

        setBounds(baseX, baseY, BAR_WIDTH * hudScale, BAR_HEIGHT * hudScale);
        pushHudScale(context, baseX, baseY);

        float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);

        LiquidGlassUtil.drawLiquidGlass(
                context,
                baseX, baseY,
                BAR_WIDTH, BAR_HEIGHT,
                liquidTime,
                4.4f, 0.18f, 0.0017f,
                RADIUS, 0.50f, 1.028f
        );

        var inv = mc.player.getInventory();
        int selectedSlot = inv.selectedSlot;
        Color accent = themeManager.getRenderedAccentColor();

        float sideGap = (BAR_WIDTH - SLOT_SIZE * 9f) / 2f;
        float targetX = baseX + sideGap + selectedSlot * SLOT_SIZE;

        if (lastSelectedX < 0f) lastSelectedX = targetX;
        float selectedX = selectedXAnimation.animate(targetX, 135);
        lastSelectedX = selectedX;
        float selectedAlpha = Math.max(0f, Math.min(1f, selectedAlphaAnimation.animate(1f, 150)));

        // Soft selected-slot lens. No opaque square.
        Render2D.drawRoundedRect(
                matrices,
                selectedX + 1f, baseY + 1f,
                SLOT_SIZE - 2f, BAR_HEIGHT - 2f,
                4.5f,
                new Color(
                        accent.getRed(), accent.getGreen(), accent.getBlue(),
                        (int) (78 * selectedAlpha)
                )
        );

        // Main hotbar items.
        for (int i = 0; i < 9; i++) {
            float slotX = baseX + sideGap + i * SLOT_SIZE;
            float itemX = slotX + 2f;
            float itemY = baseY + 2f;
            var stack = inv.getStack(i);

            // Minecraft item is 16x16, so 2px margins keep it fully visible.
            context.drawItem(stack, Math.round(itemX), Math.round(itemY));
        }

        // Offhand: separate glass capsule. Clamp it to the screen so the item can
        // never be cut in half on the right edge.
        float renderedOffhandX = Float.NaN;
        float renderedOffhandY = Float.NaN;
        float renderedOffhandPanel = 24f;
        var offhand = inv.getStack(40);
        if (!offhand.isEmpty()) {
            boolean mainHandLeft = mc.player.getMainArm().equals(net.minecraft.util.Arm.LEFT);
            float panelSize = 24f;
            float wantedX = mainHandLeft
                    ? baseX - panelSize - 5f
                    : baseX + BAR_WIDTH + 5f;

            // Matrix scaling is anchored at baseX/baseY, therefore screen bounds
            // must be converted back into this element's local coordinate space.
            float screenW = mc.getWindow().getScaledWidth();
            float localLeft = baseX + (0f - baseX) / Math.max(0.01f, hudScale);
            float localRight = baseX + (screenW - baseX) / Math.max(0.01f, hudScale);
            float offhandX = Math.max(localLeft + 1f, Math.min(wantedX, localRight - panelSize - 1f));
            float offhandY = baseY - 2f;
            renderedOffhandX = offhandX;
            renderedOffhandY = offhandY;
            renderedOffhandPanel = panelSize;

            LiquidGlassUtil.drawLiquidGlass(
                    context,
                    offhandX, offhandY,
                    panelSize, panelSize,
                    liquidTime,
                    3.8f, 0.15f, 0.0015f,
                    7.0f, 0.46f, 1.022f
            );

            // 16x16 item centered in the 24x24 panel -> no clipping.
            context.drawItem(offhand, Math.round(offhandX + 4f), Math.round(offhandY + 4f));
        }

        // Intentionally no custom HP / hunger / XP bars here.
        popHudScale(context);

        /*
         * Draw stack numbers AFTER the scaled HUD matrix is popped.
         * We scale the vector font size itself, instead of raster-scaling already
         * rendered glyphs. This keeps counts sharp at 1.25x/1.5x/2x HUD scale.
         */
        float countFontSize = Math.max(6.8f, 6.8f * hudScale);
        for (int i = 0; i < 9; i++) {
            var stack = inv.getStack(i);
            if (stack.isEmpty() || stack.getCount() <= 1) continue;

            String count = Integer.toString(stack.getCount());
            float localSlotX = baseX + sideGap + i * SLOT_SIZE;
            // Keep the count safely inside its own slot: slightly left and lower.
            float screenRight = baseX + ((localSlotX + SLOT_SIZE - 2.8f) - baseX) * hudScale;
            float screenBottom = baseY + ((baseY + BAR_HEIGHT - 0.2f) - baseY) * hudScale;

            float countW = Fonts.BOLD.getWidth(count, countFontSize);
            float countH = Fonts.BOLD.getHeight(countFontSize);
            float tx = screenRight - countW;
            float ty = screenBottom - countH;

            Render2D.drawFont(
                    context.getMatrices(),
                    Fonts.BOLD.getFont(countFontSize),
                    count,
                    tx, ty,
                    Color.WHITE
            );
        }

        if (!offhand.isEmpty() && offhand.getCount() > 1 && !Float.isNaN(renderedOffhandX)) {
            String count = Integer.toString(offhand.getCount());
            float screenRight = baseX + ((renderedOffhandX + renderedOffhandPanel - 2.8f) - baseX) * hudScale;
            float screenBottom = baseY + ((renderedOffhandY + renderedOffhandPanel - 0.5f) - baseY) * hudScale;
            float countW = Fonts.BOLD.getWidth(count, countFontSize);
            float countH = Fonts.BOLD.getHeight(countFontSize);
            float tx = screenRight - countW;
            float ty = screenBottom - countH;

            Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(countFontSize),
                    count, tx, ty, Color.WHITE);
        }

        super.onRender2D(e);
    }
}
