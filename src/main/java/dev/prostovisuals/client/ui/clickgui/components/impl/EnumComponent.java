package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.renderer.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.*;
import dev.prostovisuals.client.managers.ThemeManager;

public class EnumComponent extends Component {

    private final EnumSetting<?> setting;
    private final Animation openAnimation = new Animation(650, 1f, false, Easing.BOTH_SINE);
    private final Map<Enum<?>, Animation> pickAnimations = new HashMap<>();
    private boolean open;

    public EnumComponent(EnumSetting<?> setting) {
        super(setting.getName());
        this.setting = setting;
        for (Enum<?> enums : setting.getValue().getClass().getEnumConstants()) pickAnimations.put(enums, new Animation(300, 1f, false, Easing.BOTH_SINE));
        this.addHeight = () -> openAnimation.getValue() > 0 ? ((setting.getValue().getClass().getEnumConstants().length * 14f)) * openAnimation.getValue() : 0;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            renderReference(context, mouseX, mouseY);
            return;
        }
        openAnimation.update(open);

        Render2D.drawFont(context.getMatrices(),
                Fonts.BOLD.getFont(7.5f),
                ClickGuiLanguage.translate(setting.getName()) + ": "
                        + ClickGuiLanguage.translate(setting.currentEnumName()),
                x + 4f,
                y + 3f,
                ThemeManager.getInstance().getRenderedTextColor()
        );

        if (openAnimation.getValue() > 0) {
            float yOffset = height;
            for (Enum<?> enums : setting.getValue().getClass().getEnumConstants()) {
                Render2D.startScissor(context, x, y + yOffset, width, 14f);
                Animation anim = pickAnimations.get(enums);
                anim.update(enums == setting.getValue());
                Color accent = ThemeManager.getInstance().getRenderedAccentColor();
                Render2D.drawFont(context.getMatrices(), Fonts.ICONS.getFont(10f), "D", x + width - 14f, y + yOffset + 2f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (255 * anim.getValue())));
                Render2D.drawFont(context.getMatrices(),
                        Fonts.BOLD.getFont(7.5f),
                        ClickGuiLanguage.translate(((Nameable) enums).getName()),
                        x + 6f,
                        y + yOffset + 2f,
                        ThemeManager.getInstance().getRenderedTextColor()
                );
                yOffset += 14f;
                Render2D.stopScissor(context);
            }
        }
    }

    private void renderReference(DrawContext context, int mouseX, int mouseY) {
        openAnimation.update(open);
        float ga = Math.max(0f, Math.min(1f, getGlobalAlpha()));
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        if (hovered) {
            Render2D.drawRoundedRect(context.getMatrices(), x, y + 1f, width, height - 2f, 5f,
                    new Color(225,241,250,(int)(8*ga)));
        }
        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(5.1f),
                ClickGuiLanguage.marquee(ClickGuiLanguage.translate(setting.getName()), Math.max(18f, width * .46f - 8f), 5.1f, hovered), x + 3f, y + 6f,
                new Color(232,241,248,(int)(245*ga)));

        String value = ClickGuiLanguage.translate(setting.currentEnumName());
        float pillW = Math.min(104f, Math.max(57f, Fonts.MEDIUM.getWidth(value, 4.8f) + 25f));
        float px = x + width - pillW - 3f;
        Render2D.drawRoundedRect(context.getMatrices(), px, y + 2f, pillW, 16f, 5f,
                new Color(224,240,249,(int)(16*ga)));
        Render2D.drawBorder(context.getMatrices(), px, y + 2f, pillW, 16f, 5f, .16f, .30f,
                new Color(239,249,254,(int)(25*ga)));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(4.8f), value,
                px + 7f, y + 7f, new Color(239,247,252,(int)(245*ga)));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(4.7f), open ? "^" : "v",
                px + pillW - 13f, y + 7f, new Color(210,228,240,(int)(230*ga)));

        float a = (float)Math.max(0f, Math.min(1f, openAnimation.getValue()));
        if (a <= .01f) return;
        float yy = y + height;
        Color accent = dev.prostovisuals.client.ui.clickgui.OneClientClickGui.referenceAccentColorAt(x + width * .5f, y + height * .5f);
        for (Enum<?> option : setting.getValue().getClass().getEnumConstants()) {
            boolean selected = option == setting.getValue();
            Render2D.drawRoundedRect(context.getMatrices(), x + 3f, yy, width - 6f, 13f, 4f,
                    selected ? new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(30*ga*a))
                            : new Color(222,239,248,(int)(9*ga*a)));
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(4.7f),
                    ClickGuiLanguage.translate(((Nameable)option).getName()), x + 9f, yy + 4f,
                    new Color(225,238,247,(int)(238*ga*a)));
            if (selected) Render2D.drawRoundedRect(context.getMatrices(), x + width - 14f, yy + 4f,
                    5f, 5f, 2.5f, new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(245*ga*a)));
            yy += 14f;
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) {
            if (button == 0 || button == 1) open = !open;
            return;
        }

        if (open && button == 0) {
            float yOffset = height;
            for (Enum<?> enums : setting.getValue().getClass().getEnumConstants()) {
                if (MathUtils.isHovered(x, y + yOffset, width, 14f, (float) mouseX, (float) mouseY)) {
                    setting.setEnumValue(((Nameable) enums).getName());
                    open = false;
                    break;
                }

                yOffset += 14f;
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {

    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {

    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {

    }

    @Override
    public void charTyped(char chr, int modifiers) {

    }
}
