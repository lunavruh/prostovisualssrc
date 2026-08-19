package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.modules.settings.impl.StringSetting;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.ColorUtils;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.renderer.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import dev.prostovisuals.client.managers.ThemeManager;

public class StringComponent extends Component {

    private final StringSetting setting;
    private boolean typing, selected;
    private final Animation animation = new Animation(300, 1f, false, Easing.BOTH_SINE);
    private float scrollOffset = 0f;

    public StringComponent(StringSetting setting) {
        super(setting.getName());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        animation.update(typing);

        float textX = x + 7.5f;
        float textY = y + 2.5f;
        float maxTextWidth = width - 16f;
        float textWidth = Fonts.REGULAR.getWidth(setting.getValue(), 8f);
        
        if (textWidth > maxTextWidth) {
            if (textWidth - scrollOffset > maxTextWidth) scrollOffset = textWidth - maxTextWidth;
            if (textWidth - scrollOffset < 0) scrollOffset = textWidth;
        } else scrollOffset = 0f;

        Render2D.drawRoundedRect(context.getMatrices(), x + 4f, y, width - 8f, height, 4f,
                new Color(16, 19, 26, typing ? 205 : 170));

        Render2D.startScissor(context, x + 4, y, width - 8, height);

        if (selected)
            Render2D.drawRoundedRect(context.getMatrices(), textX, textY, Fonts.REGULAR.getWidth(setting.getValue(), 8f), Fonts.REGULAR.getHeight(8f), 0f,
                    new Color(ThemeManager.getInstance().getCurrentTheme().getAccentColor().getRed(),
                            ThemeManager.getInstance().getCurrentTheme().getAccentColor().getGreen(),
                            ThemeManager.getInstance().getCurrentTheme().getAccentColor().getBlue(),
                            150));

        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(8f), ClickGuiLanguage.translate(setting.getName()), textX, textY, new Color(255, 255, 255, (int) (255 * animation.getReversedValue())));

        if (!setting.getValue().isEmpty())
            Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(8f), setting.getValue(), textX - scrollOffset, textY, new Color(255, 255, 255, (int) (255 * animation.getValue())));

        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(8f), "|", textX - scrollOffset + textWidth, textY - 0.25f, ColorUtils.alpha(ColorUtils.pulse(Color.WHITE, 15), (int) (ColorUtils.pulse(Color.WHITE, 15).getAlpha() * animation.getValue())));

        Render2D.stopScissor(context);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY) && button == 0) typing = !typing;
        else typing = false;
        selected = false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {}

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selected) {
                    setting.setValue("");
                    selected = false;
                }
                if (typing && setting.getValue() != null && !setting.getValue().isEmpty()) setting.setValue(setting.getValue().substring(0, setting.getValue().length() - 1));
            }
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> {
                if (typing) {
                    typing = false;
                    selected = false;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (typing) {
                    setting.setValue("");
                    selected = false;
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (Screen.hasControlDown() && typing && selected && setting.getValue() != null && !setting.getValue().isEmpty()) {
                    GLFW.glfwSetClipboardString(mc.getWindow().getHandle(), setting.getValue());
                    selected = false;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (Screen.hasControlDown() && typing && GLFW.glfwGetClipboardString(mc.getWindow().getHandle()) != null) {
                    selected = false;
                    setting.setValue(setting.getValue() + GLFW.glfwGetClipboardString(mc.getWindow().getHandle()));
                }
            }
            case GLFW.GLFW_KEY_A -> {
                if (Screen.hasControlDown() && typing && setting.getValue() != null && !setting.getValue().isEmpty()) selected = true;
            }
        }
    }

    @Override
    public void keyReleased(int keyCode, int scanCode, int modifiers) {}

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!typing) return;
        if (setting.isOnlyDigit() && !Character.isDigit(chr)) return;
        setting.setValue(setting.getValue() + chr);
        selected = false;
    }
}
