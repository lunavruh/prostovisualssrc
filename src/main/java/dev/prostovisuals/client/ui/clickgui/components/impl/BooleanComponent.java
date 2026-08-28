package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.MathUtils;
// removed unused ColorUtils import
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.renderer.Render2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import dev.prostovisuals.client.managers.ThemeManager;

public class BooleanComponent extends Component {

    private final BooleanSetting setting;
    private final Animation toggleAnimation = new Animation(600, 1f, true, Easing.prostovisuals);

    public BooleanComponent(BooleanSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            renderReference(context, mouseX, mouseY);
            return;
        }
        toggleAnimation.update(setting.getValue());
        float ga = Math.max(0f, Math.min(1f, getGlobalAlpha()));

        float switchW = 19f;
        float switchH = 10f;
        float switchX = x + width - switchW - 4f;
        float switchY = y + 3f;

        String fullLabel = ClickGuiLanguage.translate(setting.getName());
        String label = fullLabel;
        float fontSize = 7.2f;
        float maxTextW = Math.max(8f, switchX - (x + 5f) - 8f);
        while (label.length() > 3 && Fonts.BOLD.getWidth(label, fontSize) > maxTextW) {
            label = label.substring(0, label.length() - 2);
        }
        if (!label.equals(fullLabel)) label += "…";

        Color text = ThemeManager.getInstance().getRenderedTextColor();
        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(fontSize),
                label, x + 5f, y + 3f,
                new Color(text.getRed(), text.getGreen(), text.getBlue(), (int) (255 * ga)));

        Color accent = ThemeManager.getInstance().getRenderedAccentColor();
        float progress = (float) toggleAnimation.getValue();

        Render2D.drawRoundedRect(context.getMatrices(), switchX, switchY, switchW, switchH, 5f,
                new Color(45, 48, 56, (int) (155 * ga)));
        if (progress > 0.01f) {
            Render2D.drawRoundedRect(context.getMatrices(), switchX, switchY, switchW, switchH, 5f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (205 * progress * ga)));
        }

        float knob = 8f;
        float knobX = switchX + 1f + (switchW - knob - 2f) * progress;
        Render2D.drawRoundedRect(context.getMatrices(), knobX, switchY + 1f, knob, knob, 4f,
                new Color(255, 255, 255, (int) (245 * ga)));
    }

    private void renderReference(DrawContext context, int mouseX, int mouseY) {
        toggleAnimation.update(setting.getValue());
        float ga = Math.max(0f, Math.min(1f, getGlobalAlpha()));
        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY);
        if (hovered) {
            Render2D.drawRoundedRect(context.getMatrices(), x, y + 1f, width, height - 2f, 5f,
                    new Color(225, 241, 250, (int)(10 * ga)));
        }

        float labelMaxW = Math.max(16f, width - 24f);
        String label = ClickGuiLanguage.marquee(ClickGuiLanguage.translate(setting.getName()), labelMaxW, 5.2f, hovered);
        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(5.2f), label,
                x + 3f, y + 6f, new Color(232, 241, 248, (int)(245 * ga)));

        float size = 10f;
        float bx = x + width - size - 4f;
        float by = y + 5f;
        Color accent = dev.prostovisuals.client.ui.clickgui.OneClientClickGui.referenceAccentColorAt(bx + size * .5f, by + size * .5f);
        float progress = Math.max(0f, Math.min(1f, toggleAnimation.getValue()));
        Render2D.drawRoundedRect(context.getMatrices(), bx, by, size, size, 2.2f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)((13f + 222f * progress) * ga)));
        Render2D.drawBorder(context.getMatrices(), bx, by, size, size, 2.2f, .18f, .34f,
                new Color(232,245,252,(int)((110f * (1f - progress) + 30f * progress) * ga)));
        if (progress > .05f) {
            Color check = new Color(255,255,255,(int)(245*ga*progress));
            Render2D.drawLine(context.getMatrices(), bx + 2.4f, by + 5.2f, bx + 4.3f, by + 7.2f, 1f, check);
            Render2D.drawLine(context.getMatrices(), bx + 4.2f, by + 7.2f, bx + 7.8f, by + 3.0f, 1f, check);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            if (button == 0 && MathUtils.isHovered(x, y, width, height, (float)mouseX, (float)mouseY)) {
                setting.setValue(!setting.getValue());
            }
            return;
        }
        float switchX = x + width - 23f;
        float switchY = y + 3f;
        float switchSize = 19f;

        if (MathUtils.isHovered(switchX - 2f, switchY - 2f, switchSize + 2f, 12f, (float) mouseX, (float) mouseY) && button == 0) {
            setting.setValue(!setting.getValue());
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
