package dev.prostovisuals.client.ui.hud;

import dev.prostovisuals.client.events.impl.EventMouse;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.api.Position;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.PositionSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.client.ui.hud.windows.Window;
import lombok.Getter;
import lombok.Setter;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.resource.language.I18n;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.prostovisuals.client.util.Wrapper.mc;
import static dev.prostovisuals.modules.api.Module.fullNullCheck;


@Getter @Setter
public abstract class HudElement extends Module {

    private final PositionSetting position = new PositionSetting("setting.position", new Position(0, 0));
    /** Individual visual scale for every HUD element. */
    private final NumberSetting hudScale = new NumberSetting("Scale", 1.0f, 0.50f, 2.00f, 0.01f, () -> false);
    private final Animation hoverAnimation = new Animation(300, 1f, false, Easing.SMOOTH_STEP);
    private final Animation cornerAnimation = new Animation(200, 1f, false, Easing.OUT_QUART);
    protected final Animation toggledAnimation = new Animation(300, 1f, false, Easing.BOTH_SINE);
    private float dragX, dragY, width, height;
    private boolean dragging, button;

    // RMB resize handle state.
    private boolean resizing;
    private float resizeStartScale;
    private float resizeStartDistance;
    private final Animation resizeHandleAnimation = new Animation(180, 1f, false, Easing.OUT_QUART);
    private static final float RESIZE_HANDLE_SIZE = 14.0f;
    private final List<Setting<?>> settings = new ArrayList<>();
    private Window window;

    public HudElement(String name) {
        super(name, Category.Utility);
        settings.add(position);
        settings.add(hudScale);
        setToggled(true);
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck()) return;

        boolean handleHovered = isResizeHandleHovered(mouseX(), mouseY());
        hoverAnimation.update((MathUtils.isHovered(getX(), getY(), getWidth(), getHeight(), mouseX(), mouseY()) && window == null) || button || dragging || resizing);
        resizeHandleAnimation.update(handleHovered || resizing);
        cornerAnimation.update(hoverAnimation.getValue() > 0);

        if (resizing) {
            if (!(mc.currentScreen instanceof ChatScreen)) {
                resizing = false;
            } else {
                float dx = mouseX() - getX();
                float dy = mouseY() - getY();
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float ratio = resizeStartDistance <= 0.001f ? 1.0f : distance / resizeStartDistance;
                float targetScale = Math.max(0.50f, Math.min(2.00f, resizeStartScale * ratio));
                // 0.01 steps feel smooth while still producing stable saved values.
                targetScale = Math.round(targetScale * 100.0f) / 100.0f;
                if (Math.abs(hudScale.getValue() - targetScale) > 0.0001f) {
                    hudScale.setValue(targetScale);
                }
            }
        }

        if (button && !resizing) {
            // Блок: если уже тянем другой элемент, не позволяем этому элементу начинать/продолжать перетаскивание
            dev.prostovisuals.client.ui.hud.HudElement current = prostovisuals.getInstance().getHudManager().getCurrentDragging();
            if (current != null && current != this) {
                dragging = false;
                return;
            }

            // Проверяем, что пользователь находится в чате для перетаскивания
            if (!(mc.currentScreen instanceof ChatScreen)) {
                dragging = false;
                button = false;
                prostovisuals.getInstance().getHudManager().setCurrentDragging(null);
                return;
            }

            if (!dragging && MathUtils.isHovered(getX(), getY(), getWidth(), getHeight(), mouseX(), mouseY())) {
                dragX = mouseX() - getX();
                dragY = mouseY() - getY();
                dragging = true;
                prostovisuals.getInstance().getHudManager().setCurrentDragging(this);
            }

            if (dragging) {
                // Исправление: обновляем позицию только один раз за кадр
                float sw = mc.getWindow().getScaledWidth();
                float sh = mc.getWindow().getScaledHeight();
                float finalX = Math.min(Math.max(mouseX() - dragX, 0), sw - width);
                float finalY = Math.min(Math.max(mouseY() - dragY, 0), sh - height);

                // Snap-to guide lines
                float threshold = 6f; // px threshold to snap
                float edgePad = 4f;   // side guide line padding

                // Snap X to vertical center
                float centerX = sw / 2f - width / 2f; // align element center to screen center
                if (Math.abs((finalX + width / 2f) - sw / 2f) <= threshold) {
                    finalX = centerX;
                } else {
                    // Snap X to nearest side (left or right)
                    float leftX = edgePad;
                    float rightX = sw - edgePad - width;
                    if (Math.abs(finalX - leftX) <= threshold) finalX = leftX;
                    else if (Math.abs(finalX - rightX) <= threshold) finalX = rightX;
                }

                // Snap Y to horizontal center
                float centerY = sh / 2f - height / 2f;
                if (Math.abs((finalY + height / 2f) - sh / 2f) <= threshold) {
                    finalY = centerY;
                } else {
                    // Snap Y to nearest side (top or bottom)
                    float topY = edgePad;
                    float bottomY = sh - edgePad - height;
                    if (Math.abs(finalY - topY) <= threshold) finalY = topY;
                    else if (Math.abs(finalY - bottomY) <= threshold) finalY = bottomY;
                }

                // Обновляем позицию только если она действительно изменилась
                float newX = finalX / sw;
                float newY = finalY / sh;

                if (Math.abs(position.getValue().getX() - newX) > 0.001f ||
                    Math.abs(position.getValue().getY() - newY) > 0.001f) {
                    position.getValue().setX(newX);
                    position.getValue().setY(newY);
                    // Планируем автосохранение после изменения позиции
                    try {
                        dev.prostovisuals.client.managers.AutoSaveManager asm = prostovisuals.getInstance().getAutoSaveManager();
                        if (asm != null) asm.scheduleAutoSave();
                    } catch (Throwable ignored) {}
                }
            }
        } else {
            dragging = false;
        }

        // Красивая анимация появления углов при наведении с закруглениями
        if (mc.currentScreen instanceof ChatScreen && cornerAnimation.getValue() > 0) {
            float animationValue = cornerAnimation.getValue();
            float animatedCornerSize = 16f * animationValue; // анимированный размер углов
            int alpha = (int) (255 * animationValue); // анимированная прозрачность
            
            // Добавляем отступ от краев элемента
            float padding = 4f; // отступ углов от краев
            float cornerX = getX() - padding;
            float cornerY = getY() - padding;
            float cornerWidth = getWidth() + (padding * 2);
            float cornerHeight = getHeight() + (padding * 2);
            
            // Отладочная информация
            
            // Используем собственный метод рендеринга закругленных углов
            Render2D.drawRoundedCorner(
                    e.getContext().getMatrices(),
                    cornerX, cornerY,
                    cornerWidth, cornerHeight,
                    animatedCornerSize,
                    new Color(255, 255, 255, alpha) // белые углы
            );
        }

        // Рисуем направляющие (центральные и боковые) только во время перетаскивания
        if (mc.currentScreen instanceof ChatScreen && dragging) {
            float sw = mc.getWindow().getScaledWidth();
            float sh = mc.getWindow().getScaledHeight();
            float edgePad = 4f;

            // Center lines
            Color guide = new Color(255, 255, 255, 110);
            // Vertical center
            Render2D.drawRoundedRect(e.getContext().getMatrices(), sw / 2f - 0.5f, 0, 1f, sh, 0f, guide);
            // Horizontal center
            Render2D.drawRoundedRect(e.getContext().getMatrices(), 0, sh / 2f - 0.5f, sw, 1f, 0f, guide);

            // Nearest side lines (vertical and horizontal)
            // Choose nearest X side to current element
            float leftX = edgePad;
            float rightX = sw - edgePad;
            float elemCenterX = getX() + getWidth() / 2f;
            float sideX = (Math.abs(elemCenterX - leftX) < Math.abs(elemCenterX - rightX)) ? leftX : rightX;
            Render2D.drawRoundedRect(e.getContext().getMatrices(), sideX - 0.5f, 0, 1f, sh, 0f, new Color(255, 255, 255, 80));

            float topY = edgePad;
            float bottomY = sh - edgePad;
            float elemCenterY = getY() + getHeight() / 2f;
            float sideY = (Math.abs(elemCenterY - topY) < Math.abs(elemCenterY - bottomY)) ? topY : bottomY;
            Render2D.drawRoundedRect(e.getContext().getMatrices(), 0, sideY - 0.5f, sw, 1f, 0f, new Color(255, 255, 255, 80));
        }

        // Clean adaptive RMB resize handle. It prefers the outside bottom-right,
        // but automatically flips inside near screen edges so it never gets clipped.
        if (mc.currentScreen instanceof ChatScreen && (hoverAnimation.getValue() > 0.01f || resizing)) {
            float a = Math.max(resizeHandleAnimation.getValue(), resizing ? 1.0f : 0.0f);
            float handle = 16.0f;
            float sw = mc.getWindow().getScaledWidth();
            float sh = mc.getWindow().getScaledHeight();

            float hx = getX() + getWidth() + 4.0f;
            float hy = getY() + getHeight() + 4.0f;
            if (hx + handle > sw - 2f) hx = getX() + getWidth() - handle - 3.0f;
            if (hy + handle > sh - 2f) hy = getY() + getHeight() - handle - 3.0f;

            int alpha = (int) (235 * a);
            Color accent = dev.prostovisuals.client.managers.ThemeManager.getInstance().getCurrentTheme().getAccentColor();
            float t = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);

            LiquidGlassUtil.drawLiquidGlass(
                    e.getContext(), hx, hy, handle, handle, t,
                    2.2f, 0.08f, 0.0008f, 6.0f,
                    0.30f + 0.16f * a, 1.010f
            );

            // Thin accent corner + three clean diagonal grip strokes.
            Render2D.drawRoundedRect(e.getContext().getMatrices(),
                    hx + handle - 3.0f, hy + 3.0f, 1.15f, handle - 6.0f, 0.6f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
            Render2D.drawRoundedRect(e.getContext().getMatrices(),
                    hx + 3.0f, hy + handle - 3.0f, handle - 6.0f, 1.15f, 0.6f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));

            Color grip = new Color(255, 255, 255, alpha);
            Render2D.drawRoundedRect(e.getContext().getMatrices(), hx + 5.0f, hy + 10.7f, 6.0f, 1.0f, 0.5f, grip);
            Render2D.drawRoundedRect(e.getContext().getMatrices(), hx + 7.0f, hy + 8.7f, 4.0f, 1.0f, 0.5f, grip);
            Render2D.drawRoundedRect(e.getContext().getMatrices(), hx + 9.0f, hy + 6.7f, 2.0f, 1.0f, 0.5f, grip);

            if (resizing) {
                String scaleText = String.format(java.util.Locale.ROOT, "%.2fx", getHudScale());
                float tw = Fonts.REGULAR.getWidth(scaleText, 8.0f);
                float bw = tw + 9.0f;
                float bx = Math.max(3f, Math.min(sw - bw - 3f, hx - bw - 5.0f));
                float by = Math.max(3f, Math.min(sh - 14f, hy + 1.0f));
                LiquidGlassUtil.drawLiquidGlass(
                        e.getContext(), bx, by, bw, 12.0f, t,
                        2.0f, 0.07f, 0.0007f, 6.0f, 0.30f, 1.008f
                );
                Render2D.drawFont(
                        e.getContext().getMatrices(), Fonts.REGULAR.getFont(8.0f),
                        scaleText, bx + 4.5f, by + 1.7f,
                        new Color(255, 255, 255, 245)
                );
            }
        }

        if (window != null) {
            if (!(mc.currentScreen instanceof ChatScreen)) window.reset();

            if (window.closed()) {
                window = null;
                return;
            }

            window.render(e.getContext(), mouseX(), mouseY());
        }


        if (mc.currentScreen instanceof ChatScreen) {
            String text = I18n.translate("RMB.setting");
            int textWidth = mc.textRenderer.getWidth(text);
            int x = 10;
            int y = mc.getWindow().getScaledHeight() - 30;
            Render2D.drawFont(e.getContext().getMatrices(), Fonts.REGULAR.getFont(9f), text, x, y, new Color(255, 255, 255));
//            e.getContext().drawTextWithShadow(mc.textRenderer, text, x, y, 0xFFFFFF);
        }
    }

    @EventHandler
    public void onRender2DX2(EventRender2D e) {
        if (fullNullCheck()) return;

        Setting<?> setting = prostovisuals.getInstance().getHudManager().getElements().getName(getName());
        if (setting != null && setting instanceof BooleanSetting) {
            toggledAnimation.update(((BooleanSetting) setting).getValue());
        } else {
            // Если элемент не зарегистрирован в списке элементов (например, динамический PerfHUD),
            // используем состояние модуля isToggled() вместо принудительного скрытия
            toggledAnimation.update(isToggled());
        }
    }

    @EventHandler
    public void onMouse(EventMouse e) {
        if (!(mc.currentScreen instanceof ChatScreen) || fullNullCheck()) return;

        if (e.getAction() == 0) {
            button = false;
            dragging = false;
            resizing = false;
            prostovisuals.getInstance().getHudManager().setCurrentDragging(null);
            // После завершения перетаскивания ещё раз планируем автосохранение
            try {
                dev.prostovisuals.client.managers.AutoSaveManager asm = prostovisuals.getInstance().getAutoSaveManager();
                if (asm != null) asm.scheduleAutoSave();
            } catch (Throwable ignored) {}
        } else if (e.getAction() == 1) {
            // LMB moves HUD. RMB is reserved for resize handle/settings.
            if (e.getButton() == 0 && (prostovisuals.getInstance().getHudManager().getCurrentDragging() == null ||
                    prostovisuals.getInstance().getHudManager().getCurrentDragging() == this)) {
                button = true;
            }

            if (window != null) {
                if (MathUtils.isHovered(window.getX(), window.getY(), window.getWidth(), window.getFinalHeight(), mouseX(), mouseY())) {
                    window.mouseClicked(mouseX(), mouseY(), e.getButton());
                    return;
                } else window.reset();
            }

            if (e.getButton() == 1) {
                if (isResizeHandleHovered(mouseX(), mouseY())) {
                    resizing = true;
                    dragging = false;
                    button = false;
                    resizeStartScale = getHudScale();
                    float dx = mouseX() - getX();
                    float dy = mouseY() - getY();
                    resizeStartDistance = Math.max(1.0f, (float) Math.sqrt(dx * dx + dy * dy));
                    prostovisuals.getInstance().getHudManager().setCurrentDragging(this);
                    if (window != null) window.reset();
                    return;
                }

                if (MathUtils.isHovered(getX(), getY(), getWidth(), getHeight(), mouseX(), mouseY())) {
                    if (settings.size() > 1) {
                        if (prostovisuals.getInstance().getHudManager().getWindow() != null)
                            prostovisuals.getInstance().getHudManager().getWindow().reset();
                        for (HudElement element : prostovisuals.getInstance().getHudManager().getHudElements()) {
                            if (element.getWindow() == null) continue;
                            element.getWindow().reset();
                        }
                        window = new Window(mouseX() + 3, mouseY() + 3, 100, 12.5f, settings);
                    }
                }
            }
        }
    }

    public boolean isResizeHandleHovered(float mx, float my) {
        if (!(mc.currentScreen instanceof ChatScreen)) return false;
        float handle = 16.0f;
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        float hx = getX() + getWidth() + 4.0f;
        float hy = getY() + getHeight() + 4.0f;
        if (hx + handle > sw - 2f) hx = getX() + getWidth() - handle - 3.0f;
        if (hy + handle > sh - 2f) hy = getY() + getHeight() - handle - 3.0f;

        return MathUtils.isHovered(hx - 2f, hy - 2f, handle + 4f, handle + 4f, mx, my);
    }

    /** Returns the user-configured scale of this HUD element. */
    protected float getHudScale() {
        return Math.max(0.50f, Math.min(2.00f, hudScale.getValue()));
    }

    /** Scales the current DrawContext around the element's top-left anchor. */
    protected void pushHudScale(net.minecraft.client.gui.DrawContext context, float originX, float originY) {
        float scale = getHudScale();
        context.getMatrices().push();
        context.getMatrices().translate(originX, originY, 0f);
        context.getMatrices().scale(scale, scale, 1f);
        context.getMatrices().translate(-originX, -originY, 0f);
    }

    protected void popHudScale(net.minecraft.client.gui.DrawContext context) {
        context.getMatrices().pop();
    }

    public float getX() {
        return mc.getWindow().getScaledWidth() * position.getValue().getX();
    }

    public float getY() {
        return mc.getWindow().getScaledHeight() * position.getValue().getY();
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public int mouseX() {
        return (int) (mc.mouse.getX() / mc.getWindow().getScaleFactor());
    }

    public int mouseY() {
        return (int) (mc.mouse.getY() / mc.getWindow().getScaleFactor());
    }

    public void setBounds(float x, float y, float width, float height) {
        this.width = width;
        this.height = height;
        position.getValue().setX(x / mc.getWindow().getScaledWidth());
        position.getValue().setY(y / mc.getWindow().getScaledHeight());
    }

    protected boolean closed() {
        return toggledAnimation.getValue() <= 0f;
    }

    /**
     * Вызывается при перетаскивании элемента для рисования углов
     */
    protected void onDragging(EventRender2D e) {
        if (dragging) {
            // Добавляем отступ от краев элемента
            float padding = 4f;
            float cornerX = getX() - padding;
            float cornerY = getY() - padding;
            float cornerWidth = getWidth() + (padding * 2);
            float cornerHeight = getHeight() + (padding * 2);
            
            // Используем собственный метод рендеринга закругленных углов
            Render2D.drawRoundedCorner(
                    e.getContext().getMatrices(),
                    cornerX, cornerY,
                    cornerWidth, cornerHeight,
                    16f, // размер углов
                    new Color(255, 255, 255, (int) (255 * hoverAnimation.getValue())), // белые углы
                    2f // толщина линий
            );
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}
