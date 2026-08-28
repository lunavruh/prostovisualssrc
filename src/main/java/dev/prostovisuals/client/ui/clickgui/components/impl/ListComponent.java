package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListComponent extends Component {

    private final ListSetting setting;
    private final Map<BooleanSetting, Animation> pickAnimations = new HashMap<>();

    @Getter private final Animation openAnimation = new Animation(650, 1f, false, Easing.BOTH_SINE);
    private boolean open;

    public ListComponent(ListSetting setting) {
        super(setting.getName());
        this.setting = setting;
        for (BooleanSetting setting1 : setting.getValue()) pickAnimations.put(setting1, new Animation(300, 1f, false, Easing.BOTH_SINE));
        this.visible = setting::isVisible;
        this.addHeight = () -> openAnimation.getValue() > 0 ? (setting.getValue().size() * 14f) * (float) openAnimation.getValue() : 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            renderReference(context, mouseX, mouseY);
            return;
        }
        openAnimation.update(open);

        float ga = Math.max(0f, Math.min(1f, getGlobalAlpha()));

        Render2D.drawFont(context.getMatrices(),
                Fonts.BOLD.getFont(7.5f),
                ClickGuiLanguage.translate(setting.getName()),
                x + 5f,
                y + 3.5f,
                new Color(
                        ThemeManager.getInstance().getRenderedTextColor().getRed(),
                        ThemeManager.getInstance().getRenderedTextColor().getGreen(),
                        ThemeManager.getInstance().getRenderedTextColor().getBlue(),
                        (int) (ThemeManager.getInstance().getRenderedTextColor().getAlpha() * ga)
                )
        );

        Render2D.drawFont(context.getMatrices(),
                Fonts.BOLD.getFont(7.5f),
                "(" + setting.getToggled().size() + "/" + setting.getValue().size() + ")",
                x + width - Fonts.REGULAR.getWidth("(" + setting.getToggled().size() + "/" + setting.getValue().size() + ")", 7.5f) - 5f,
                y + 3.5f,
                new Color(
                        ThemeManager.getInstance().getRenderedTextColor().getRed(),
                        ThemeManager.getInstance().getRenderedTextColor().getGreen(),
                        ThemeManager.getInstance().getRenderedTextColor().getBlue(),
                        (int) (ThemeManager.getInstance().getRenderedTextColor().getAlpha() * ga)
                )
        );

        if (openAnimation.getValue() > 0) {
            float yOffset = height;
            float a = (float) Math.max(0f, Math.min(1f, openAnimation.getValue()));
            for (BooleanSetting setting : setting.getValue()) {
                Animation anim = pickAnimations.get(setting);
                anim.update(setting.getValue());
                float textSlide = (1f - a) * 8f;
                int itemAlpha = (int) (ThemeManager.getInstance().getRenderedTextColor().getAlpha() * ga * a);
                Render2D.drawFont(context.getMatrices(), Fonts.ICONS.getFont(10f), "D", x + width - 14f, y + yOffset + 3.5f,
                        new Color(0, 0, 0, (int) (255 * anim.getValue() * ga * a)));
                Render2D.drawFont(context.getMatrices(),
                        Fonts.BOLD.getFont(7.5f),
                        ClickGuiLanguage.translate(setting.getName()),
                        x + 6f + textSlide,
                        y + yOffset + 3.5f,
                        new Color(
                                ThemeManager.getInstance().getRenderedTextColor().getRed(),
                                ThemeManager.getInstance().getRenderedTextColor().getGreen(),
                                ThemeManager.getInstance().getRenderedTextColor().getBlue(),
                                itemAlpha
                        )
                );
                yOffset += 14f;
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

        String summary;
        if (setting.isSingleSelect() && !setting.getToggled().isEmpty()) {
            summary = ClickGuiLanguage.translate(setting.getToggled().get(0).getName());
        } else {
            summary = setting.getToggled().size() + "/" + setting.getValue().size();
        }
        float pillW = Math.min(105f, Math.max(47f, Fonts.MEDIUM.getWidth(summary, 4.8f) + 24f));
        float px = x + width - pillW - 3f;
        Render2D.drawRoundedRect(context.getMatrices(), px, y + 2f, pillW, 16f, 5f,
                new Color(224,240,249,(int)(16*ga)));
        Render2D.drawBorder(context.getMatrices(), px, y + 2f, pillW, 16f, 5f, .16f, .30f,
                new Color(239,249,254,(int)(25*ga)));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(4.8f), summary,
                px + 7f, y + 7f, new Color(239,247,252,(int)(245*ga)));
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(4.7f), open ? "^" : "v",
                px + pillW - 13f, y + 7f, new Color(210,228,240,(int)(230*ga)));

        float a = (float)Math.max(0f, Math.min(1f, openAnimation.getValue()));
        if (a <= .01f) return;
        float yy = y + height;
        Color accent = dev.prostovisuals.client.ui.clickgui.OneClientClickGui.referenceAccentColorAt(x + width * .5f, y + height * .5f);
        for (BooleanSetting option : setting.getValue()) {
            boolean selected = option.getValue();
            Render2D.drawRoundedRect(context.getMatrices(), x + 3f, yy, width - 6f, 13f, 4f,
                    selected ? new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(30*ga*a))
                            : new Color(222,239,248,(int)(9*ga*a)));
            Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(4.7f),
                    ClickGuiLanguage.translate(option.getName()), x + 9f, yy + 4f,
                    new Color(225,238,247,(int)(238*ga*a)));
            float bx=x+width-15f,by=yy+3f;
            Render2D.drawRoundedRect(context.getMatrices(),bx,by,7f,7f,1.8f,
                    selected?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(235*ga*a))
                            :new Color(230,244,251,(int)(20*ga*a)));
            yy += 14f;
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        // Header only opens/closes the submenu. It never silently cycles the
        // selected mode on a plain left click.
        if (MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY)) {
            if (button == 0 || button == 1) {
                open = !open;
                return;
            }
        }
        // Select item on left click when open
        if (openAnimation.getValue() > 0 && button == 0) {
            float yOffset = height;
            float visibleH = (float) (setting.getValue().size() * 14f * Math.max(0f, Math.min(1f, openAnimation.getValue())));
            for (BooleanSetting s : setting.getValue()) {
                if (yOffset >= height + visibleH) break; // не кликаем по невидимой части во время анимации
                if (MathUtils.isHovered(x, y + yOffset, width, 14f, (float) mouseX, (float) mouseY)) {
                    if (setting.isSingleSelect()) {
                        for (BooleanSetting all : setting.getValue()) all.setValue(false);
                        s.setValue(true);
                        open = false;
                    } else {
                        s.setValue(!s.getValue());
                    }
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
