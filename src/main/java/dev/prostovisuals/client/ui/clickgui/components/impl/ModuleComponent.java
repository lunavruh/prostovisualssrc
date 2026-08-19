package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.modules.settings.impl.*;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.renderer.Render2D;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import dev.prostovisuals.client.managers.ThemeManager;

public class ModuleComponent extends Component {

    private final Module module;
    private boolean open;
    private boolean binding;
    private boolean showBindModeMenu;
    private boolean renderExternally;

    // координаты отображения текста бинда для хитбокса
    private float bindTextX, bindTextY, bindTextW, bindTextH;

    @Getter private final List<Component> components = new ArrayList<>();

    private final Animation hoverAnim   = new Animation(200, 1f, false, Easing.BOTH_SINE);
    private final Animation toggleAnim  = new Animation(200, 1f, false, Easing.BOTH_SINE);
    @Getter private final Animation openAnimation = new Animation(300, 1f, false, Easing.BOTH_SINE);
    private final Animation bindMenuAnimation = new Animation(200, 1f, false, Easing.BOTH_SINE);
    private boolean toggleAnimInitialized;
    private boolean lastToggleState;
    private boolean isToggleAnimating;
    private boolean hoverAnimInitialized;

    private static final float HEADER_HEIGHT = 24f;
    private static final float CHILD_HEIGHT  = 20f;

    public ModuleComponent(Module module) {
        super(module.getName());
        this.module = module;
        this.lastToggleState = module.isToggled();

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) components.add(new BooleanComponent((BooleanSetting) setting));
            else if (setting instanceof NumberSetting) components.add(new SliderComponent((NumberSetting) setting));
            else if (setting instanceof EnumSetting) components.add(new EnumComponent((EnumSetting<?>) setting));
            else if (setting instanceof StringSetting) components.add(new StringComponent((StringSetting) setting));
            else if (setting instanceof ListSetting) components.add(new ListComponent((ListSetting) setting));
            else if (setting instanceof BindSetting) components.add(new BindComponent((BindSetting) setting));
            else if (setting instanceof ColorSetting) components.add(new ColorComponent((ColorSetting) setting));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = MathUtils.isHovered(x, y, width, HEADER_HEIGHT, mouseX, mouseY);
        // Показываем описание модуля сверху ClickGUI при ховере
        if (hovered && mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.ClickGui clickGui) {
            clickGui.setDescription(module.getDescription());
        }
        // Ховер-анимация зависит только от наведения, не от состояния модуля
        if (!hoverAnimInitialized) {
            hoverAnim.setDuration(0);
            hoverAnim.update(hovered);
            hoverAnim.setDuration(200);
            hoverAnimInitialized = true;
        } else {
            hoverAnim.update(hovered);
        }
        // Обновляем анимацию переключателя только при фактической смене состояния модуля
        boolean currentState = module.isToggled();
        if (!toggleAnimInitialized) {
            toggleAnim.setDuration(0);
            toggleAnim.update(currentState);
            toggleAnim.setDuration(200);
            toggleAnimInitialized = true;
            lastToggleState = currentState;
            isToggleAnimating = false;
        } else if (currentState != lastToggleState) {
            toggleAnim.setDuration(200);
            toggleAnim.update(currentState);
            lastToggleState = currentState;
            isToggleAnimating = true;
        } else if (isToggleAnimating && (toggleAnim.finished(currentState))) {
            isToggleAnimating = false;
        }
        openAnimation.update(open);
        bindMenuAnimation.update(showBindModeMenu);

        float alpha = Math.max(0f, Math.min(1f, globalAlpha));
        float hoverT = Math.max(0f, Math.min(1f, hoverAnim.getValue()));
        Color themeAccent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();
        float totalHeight = getHeight();

        // Module cards stay matte. One shared LiquidGlass pass belongs to the
        // ClickGUI shell; hundreds of per-card post-process passes killed FPS.
        Render2D.drawRoundedRect(context.getMatrices(), x, y, width, totalHeight, 8.0f,
                new Color(13, 16, 22, (int) ((176 + 30 * hoverT) * alpha)));
        Render2D.drawRoundedRect(context.getMatrices(), x + 1f, y + 1f, width - 2f, HEADER_HEIGHT - 2f, 7f,
                new Color(28, 32, 42, (int) ((46 + 34 * hoverT) * alpha)));
        if (module.isToggled()) {
            Color accentOverlay = ThemeManager.getInstance().getCurrentTheme().getAccentColor();
            Render2D.drawRoundedRect(context.getMatrices(), x + 1f, y + 1f, width - 2f, HEADER_HEIGHT - 2f, 7f,
                    new Color(accentOverlay.getRed(), accentOverlay.getGreen(), accentOverlay.getBlue(),
                            (int) (64 * Math.max(0f, Math.min(1f, globalAlpha)))));
            Render2D.drawRoundedRect(
                    context.getMatrices(), x + 3f, y + 5f, 2.2f, HEADER_HEIGHT - 10f, 1.1f,
                    new Color(accentOverlay.getRed(), accentOverlay.getGreen(), accentOverlay.getBlue(),
                            (int) (245 * Math.max(0f, Math.min(1f, globalAlpha))))
            );
        }


        Color textColor = new Color(255, 255, 255, (int) ((200 + 55 * hoverAnim.getValue()) * Math.max(0f, Math.min(1f, globalAlpha))));
        float titleFontSize = 8f;
        float titleFontH = Fonts.REGULAR.getHeight(titleFontSize);
        float titleY = y + (HEADER_HEIGHT - titleFontH) / 2f;
        String localizedModuleName = module.getName();
        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(titleFontSize),
                localizedModuleName, x + 8f, titleY, textColor);

        // биндинг — вычисляем позицию и хитбокс
        String bindText = "";
        if (binding) bindText = "Press key...";
        else if (module.getBind() != null && module.getBind().getKey() != -1)
            bindText = module.getBind().toString().replace("_", " ");

        // маленький текст бинда снизу
        float bindFontSize = 6f;
        bindTextW = bindText.isEmpty() ? 0f : Fonts.REGULAR.getWidth(bindText, bindFontSize);
        bindTextH = Fonts.REGULAR.getHeight(bindFontSize);
        // координаты будут обновлены после вычисления totalHeight
        // временно сбросим X/Y, чтобы не ломать хитбокс до отрисовки
        bindTextX = Float.NaN;
        bindTextY = Float.NaN;

        // Compact bind button shown directly to the left of the toggle.
        String bindButtonText = binding ? "..." : (bindText.isEmpty() ? "Bind" : bindText);
        float bindButtonFont = 6.4f;
        float bindButtonW = Math.max(28f, Fonts.MEDIUM.getWidth(bindButtonText, bindButtonFont) + 10f);
        float bindButtonH = 12f;
        float switchPreviewX = x + width - 24f;
        bindTextX = switchPreviewX - bindButtonW - 5f;
        bindTextY = y + (HEADER_HEIGHT - bindButtonH) / 2f;
        bindTextW = bindButtonW;
        bindTextH = bindButtonH;

        Render2D.drawRoundedRect(context.getMatrices(), bindTextX, bindTextY, bindButtonW, bindButtonH, 5.5f,
                new Color(24, 28, 36, (int) ((binding ? 225 : 170) * alpha)));
        Color bindAccent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();
        Color bindColor = binding
                ? new Color(bindAccent.getRed(), bindAccent.getGreen(), bindAccent.getBlue(),
                        (int) (255 * Math.max(0f, Math.min(1f, globalAlpha))))
                : new Color(225, 228, 235, (int) (220 * Math.max(0f, Math.min(1f, globalAlpha))));
        Render2D.drawFont(
                context.getMatrices(), Fonts.MEDIUM.getFont(bindButtonFont),
                bindButtonText,
                bindTextX + (bindButtonW - Fonts.MEDIUM.getWidth(bindButtonText, bindButtonFont)) * 0.5f,
                bindTextY + 2.6f,
                bindColor
        );

        // Синхронизация состояния ползунка без анимации при первом рендере,
        // чтобы избежать ложного отображения статуса при открытии GUI
        if (!toggleAnimInitialized) {
            toggleAnim.setDuration(0);
            toggleAnim.update(module.isToggled());
            // восстановить стандартную длительность анимации (200 мс по умолчанию)
            toggleAnim.setDuration(200);
            toggleAnimInitialized = true;
        }

                // переключатель (ползунок) состояния модуля, как у BooleanSetting
        float switchW = 20f;
        float switchH = 10f;
        float reservedRight = 0f; // фиксированное положение: без учёта текста бинда
        float switchX = x + width - reservedRight - 24f; // 24f = ширина переключателя + отступ
        float switchY = y + (HEADER_HEIGHT - switchH) / 2f;
        Color accent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();
        float progress = isToggleAnimating ? (float) toggleAnim.getValue() : (currentState ? 1f : 0f);
        Render2D.drawRoundedRect(context.getMatrices(), switchX, switchY, switchW, switchH, 5f,
                new Color(34, 38, 48, (int) (205 * alpha)));
        if (progress > 0.001f) {
            Render2D.drawRoundedRect(context.getMatrices(), switchX, switchY, switchW, switchH, 5f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                            (int) (220 * progress * alpha)));
        }
        float thumbW = 8f;
        float thumbH = 8f;
        // Увеличенный симметричный отступ 1.5f с обеих сторон
        float padding = 1.5f;
        float thumbX = switchX + padding + (switchW - thumbW - 2f * padding) * progress;
        float thumbY = switchY + padding + (switchH - thumbH - 2f * padding) / 2f;
        Render2D.drawRoundedRect(context.getMatrices(), thumbX, thumbY, thumbW, thumbH, 3f, Color.WHITE);

        // дочерние компоненты (без анимации раскрытия)
        if (open && !renderExternally) {
            float childY = y + HEADER_HEIGHT;
            float visibleH = getChildrenFullHeight();

            // клип ограничен рамкой модуля
            context.enableScissor((int) x, (int) (y + HEADER_HEIGHT),
                    (int) (x + width), (int) (y + HEADER_HEIGHT + visibleH));

            for (Component component : components) {
                if (!component.getVisible().get()) continue;
                component.setX(x + 5f);
                component.setY(childY);
                component.setWidth(width - 10f);
                component.setHeight(CHILD_HEIGHT);
                component.setGlobalAlpha(globalAlpha);
                component.render(context, mouseX, mouseY, delta);
                childY += component.getHeight() + component.getAddHeight().get();
            }

            context.disableScissor();
        }

    }

    /**
     * Render bind popup in ClickGui's final overlay pass.
     * This must be called after the module-list scissor is disabled so later
     * module cards cannot cover or clip the popup.
     */
    private float getBindMenuX(float itemW) {
        float desiredX = Float.isNaN(bindTextX)
                ? (x + width - 90f)
                : (bindTextX + bindTextW + 6f);

        float menuX = Math.min(desiredX, x + width - itemW - 6f);

        if (!Float.isNaN(bindTextX) && menuX < bindTextX) {
            float leftCandidate = bindTextX - itemW - 6f;
            if (leftCandidate >= x + 6f) {
                menuX = leftCandidate;
            }
        }

        return menuX;
    }

    private float getBindMenuY(float itemH) {
        float base = Float.isNaN(bindTextY)
                ? (y + HEADER_HEIGHT + 2f)
                : (bindTextY - (itemH * 3f + 8f) - 4f);

        // Same animation offset used for both drawing and hitboxes.
        float a = (float) Math.max(0f, Math.min(1f, bindMenuAnimation.getValue()));
        return base + (1f - a) * 6f;
    }

    public void renderBindMenuOverlay(DrawContext context) {
        if (!showBindModeMenu) return;

        context.getMatrices().push();
        context.getMatrices().translate(0f, 0f, 600f);
            float itemW = 80f;
            float itemH = 15f; // увеличено с 12f до 15f

            float menuX = getBindMenuX(itemW);
            float menuY = getBindMenuY(itemH);

            float a = (float) Math.max(0f, Math.min(1f, bindMenuAnimation.getValue()));

            Render2D.drawRoundedRect(context.getMatrices(), menuX, menuY, itemW, itemH * 3 + 8f, 6f,
                    new Color(10, 13, 19, (int) (238 * a * Math.max(0f, Math.min(1f, globalAlpha)))));

            String overlayBindText = "";
            if (module.getBind() != null && !module.getBind().isEmpty()) {
                overlayBindText = module.getBind().toString().replace("_", " ");
            }
            String bindRow = binding
                    ? "Press key..."
                    : "Bind: " + (overlayBindText.isEmpty() ? "NONE" : overlayBindText);
            String opt1 = "Toggle";
            String opt2 = "Hold";

            boolean isToggle = module.getBind() != null && module.getBind().getMode() == Bind.Mode.TOGGLE;
            boolean isHold = module.getBind() != null && module.getBind().getMode() == Bind.Mode.HOLD;

            int textAlpha = (int) (255 * a * Math.max(0f, Math.min(1f, globalAlpha)));
            Color popupAccent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();

            // Row 1: bind itself.
            Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(7f), bindRow,
                    menuX + 7f, menuY + 5f,
                    new Color(255, 255, 255, textAlpha));

            // Row 2: Toggle.
            Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(7f), opt1,
                    menuX + 19f, menuY + 5f + itemH,
                    new Color(255, 255, 255, textAlpha));
            Render2D.drawRoundedRect(context.getMatrices(),
                    menuX + 7f, menuY + itemH + 5f, 7f, 7f, 3.5f,
                    isToggle
                            ? new Color(popupAccent.getRed(), popupAccent.getGreen(), popupAccent.getBlue(), textAlpha)
                            : new Color(70, 74, 84, (int) (160 * a)));

            // Row 3: Hold.
            Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(7f), opt2,
                    menuX + 19f, menuY + 5f + itemH * 2f,
                    new Color(255, 255, 255, textAlpha));
            Render2D.drawRoundedRect(context.getMatrices(),
                    menuX + 7f, menuY + itemH * 2f + 5f, 7f, 7f, 3.5f,
                    isHold
                            ? new Color(popupAccent.getRed(), popupAccent.getGreen(), popupAccent.getBlue(), textAlpha)
                            : new Color(70, 74, 84, (int) (160 * a)));
        context.getMatrices().pop();
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        // While waiting for a bind, any mouse button becomes the new bind.
        if (binding) {
            Bind.Mode mode = module.getBind() != null && module.getBind().getMode() != null
                    ? module.getBind().getMode()
                    : Bind.Mode.TOGGLE;

            module.setBind(new Bind(button, true, mode));
            binding = false;
            showBindModeMenu = true;
            return;
        }

        // Floating bind popup is modal and gets clicks before the module itself.
        if (showBindModeMenu) {
            float itemW = 80f;
            float itemH = 15f;
            float menuX = getBindMenuX(itemW);
            float menuY = getBindMenuY(itemH);

            // Row 1: Bind: NONE / Bind: KEY
            // BOTH LMB and RMB enter capture mode.
            if (MathUtils.isHovered(
                    menuX, menuY,
                    itemW, itemH,
                    (float) mouseX, (float) mouseY
            )) {
                if (button == 0 || button == 1) {
                    binding = true;
                    showBindModeMenu = true;
                }
                return;
            }

            // Row 2: Toggle
            if (MathUtils.isHovered(
                    menuX, menuY + itemH,
                    itemW, itemH,
                    (float) mouseX, (float) mouseY
            )) {
                if (button == 0 || button == 1) {
                    Bind old = module.getBind();
                    int key = old == null ? -1 : old.getKey();
                    boolean mouse = old != null && old.isMouse();
                    module.setBind(new Bind(key, mouse, Bind.Mode.TOGGLE));
                    showBindModeMenu = true;
                }
                return;
            }

            // Row 3: Hold
            if (MathUtils.isHovered(
                    menuX, menuY + itemH * 2f,
                    itemW, itemH,
                    (float) mouseX, (float) mouseY
            )) {
                if (button == 0 || button == 1) {
                    Bind old = module.getBind();
                    int key = old == null ? -1 : old.getKey();
                    boolean mouse = old != null && old.isMouse();
                    module.setBind(new Bind(key, mouse, Bind.Mode.HOLD));
                    showBindModeMenu = true;
                }
                return;
            }

            // Click outside -> close popup, don't leak click to another module.
            showBindModeMenu = false;
            return;
        }

        boolean onHeader = MathUtils.isHovered(
                x, y, width, HEADER_HEIGHT,
                (float) mouseX, (float) mouseY
        );

        boolean onBindText =
                !Float.isNaN(bindTextX)
                        && MathUtils.isHovered(
                                bindTextX,
                                bindTextY - 1f,
                                bindTextW,
                                bindTextH + 2f,
                                (float) mouseX,
                                (float) mouseY
                        );

        // Main Bind pill: LMB or RMB starts capture.
        if (onBindText && (button == 0 || button == 1)) {
            binding = true;
            showBindModeMenu = false;
            return;
        }

        // MMB opens Bind/Toggle/Hold popup.
        if ((onBindText || onHeader) && button == 2) {
            showBindModeMenu = true;
            return;
        }

        if (onHeader) {
            if (button == 0) {
                module.toggle();
                return;
            }

            // RMB on the module itself remains settings-only.
            if (button == 1 && !components.isEmpty() && !renderExternally) {
                open = !open;
                return;
            }
        }

        if (open && !renderExternally) {
            for (Component component : components) {
                component.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        // При открытом меню режима бинда — поглощаем отпускание, чтобы не передавалось детям
        if (showBindModeMenu) return;
        if (open && !renderExternally) {
            for (Component component : components) component.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
                Bind.Mode mode = module.getBind() != null && module.getBind().getMode() != null
                        ? module.getBind().getMode()
                        : Bind.Mode.TOGGLE;
                module.setBind(new Bind(-1, false, mode)); // clear key, keep Toggle/Hold
            } else {
                // сохраняем текущий режим при переназначении
                Bind.Mode mode = module.getBind() != null ? module.getBind().getMode() : Bind.Mode.TOGGLE;
                module.setBind(new Bind(keyCode, false, mode));
            }
            binding = false;
            showBindModeMenu = true;
            return;
        }

        if (open && !renderExternally) {
            for (Component component : components) component.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {
        if (open && !renderExternally) {
            for (Component component : components) component.keyReleased(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (open && !renderExternally) {
            for (Component component : components) component.charTyped(chr, modifiers);
        }
    }

    // высота = только заголовок, если внешний рендер настроек активен
    @Override
    public float getHeight() {
        if (renderExternally) return HEADER_HEIGHT;
        return HEADER_HEIGHT + (open ? getChildrenFullHeight() : 0f);
    }

    public float getChildrenFullHeight() {
        float h = 0f;
        for (Component c : components) {
            if (!c.getVisible().get()) continue;
            h += CHILD_HEIGHT + c.getAddHeight().get();
        }
        return h;
    }

    private static Color lerpColor(Color c1, Color c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (c1.getRed()   + (c2.getRed()   - c1.getRed())   * t);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) (c1.getBlue()  + (c2.getBlue()  - c1.getBlue())  * t);
        int a = (int) (c1.getAlpha() + (c2.getAlpha() - c1.getAlpha()) * t);
        return new Color(r, g, b, a);
    }
    public Module getModule() {
        return module;
    }

    public void setRenderExternally(boolean value) {
        this.renderExternally = value;
    }

    public boolean isRenderExternally() {
        return renderExternally;
    }

    public boolean isBindModeMenuOpen() {
        return showBindModeMenu;
    }

    public boolean isBinding() {
        return binding;
    }

    public float renderSettingsExternally(DrawContext context, float contentX, float contentY, float contentW,
                                          float clipX, float clipY, float clipW, float clipH, int mouseX, int mouseY, float delta, float scrollY) {
        float childY = contentY - scrollY;
        float totalHeight = 0f;
        context.enableScissor((int) clipX, (int) clipY, (int) (clipX + clipW), (int) (clipY + clipH));
        for (Component component : components) {
            if (!component.getVisible().get()) continue;
            component.setX(contentX + 5f);
            component.setY(childY);
            component.setWidth(contentW - 10f);
            component.setHeight(CHILD_HEIGHT);
            component.setGlobalAlpha(globalAlpha);
            component.render(context, mouseX, mouseY, delta);
            float h = component.getHeight() + component.getAddHeight().get();
            childY += h;
            totalHeight += h;
        }
        context.disableScissor();
        return totalHeight;
    }

    public void mouseClickedExternal(double mouseX, double mouseY, int button) {
        for (Component component : components) component.mouseClicked(mouseX, mouseY, button);
    }

    public void mouseReleasedExternal(double mouseX, double mouseY, int button) {
        for (Component component : components) component.mouseReleased(mouseX, mouseY, button);
    }

    public void keyPressedExternal(int keyCode, int scanCode, int modifiers) {
        for (Component component : components) component.keyPressed(keyCode, scanCode, modifiers);
    }

    public void keyReleasedExternal(int keyCode, int scanCode, int modifiers) {
        for (Component component : components) component.keyReleased(keyCode, scanCode, modifiers);
    }

    public void charTypedExternal(char chr, int modifiers) {
        for (Component component : components) component.charTyped(chr, modifiers);
    }
}
