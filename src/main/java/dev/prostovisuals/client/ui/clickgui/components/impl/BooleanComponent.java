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
    private final Animation toggleAnimation = new Animation(300, 1f, true, Easing.prostovisuals);

    public BooleanComponent(BooleanSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
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

        Color text = ThemeManager.getInstance().getCurrentTheme().getTextColor();
        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(fontSize),
                label, x + 5f, y + 3f,
                new Color(text.getRed(), text.getGreen(), text.getBlue(), (int) (255 * ga)));

        Color accent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();
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

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
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
