package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.colorgui.ColorPickerScreen;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.settings.impl.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public final class ColorComponent extends Component {
    private final ColorSetting setting;

    public ColorComponent(ColorSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float ga = Math.max(0f, Math.min(1f, getGlobalAlpha()));
        Color value = setting.getColor();
        Color text = ThemeManager.getInstance().getCurrentTheme().getTextColor();
        String label = ClickGuiLanguage.translate(setting.getName());
        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(7.2f), label, x + 5f, y + 3f,
                new Color(text.getRed(), text.getGreen(), text.getBlue(), (int)(255 * ga)));

        float sw = 24f, sh = 10f;
        float sx = x + width - sw - 4f, sy = y + 3f;
        Render2D.drawRoundedRect(context.getMatrices(), sx - 1f, sy - 1f, sw + 2f, sh + 2f, 5.5f,
                new Color(255, 255, 255, (int)(55 * ga)));
        Render2D.drawRoundedRect(context.getMatrices(), sx, sy, sw, sh, 5f,
                new Color(value.getRed(), value.getGreen(), value.getBlue(), (int)(245 * ga)));
    }

    @Override public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && MathUtils.isHovered(x, y, width, 16f, (float)mouseX, (float)mouseY)) {
            mc.setScreen(new ColorPickerScreen(setting, mc.currentScreen));
        }
    }
    @Override public void mouseReleased(double mouseX,double mouseY,int button) {}
    @Override public void keyPressed(int keyCode,int scanCode,int modifiers) {}
    @Override public void keyReleased(int keyCode,int scanCode,int modifiers) {}
    @Override public void charTyped(char chr,int modifiers) {}
}
