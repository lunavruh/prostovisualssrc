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
    private final Animation animation = new Animation(500, 1f, false, Easing.BOTH_SINE);
    private float scrollOffset = 0f;

    public StringComponent(StringSetting setting) {
        super(setting.getName());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            renderReference(context, mouseX, mouseY);
            return;
        }
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
                    new Color(ThemeManager.getInstance().getRenderedAccentColor().getRed(),
                            ThemeManager.getInstance().getRenderedAccentColor().getGreen(),
                            ThemeManager.getInstance().getRenderedAccentColor().getBlue(),
                            150));

        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(8f), ClickGuiLanguage.translate(setting.getName()), textX, textY, new Color(255, 255, 255, (int) (255 * animation.getReversedValue())));

        if (!setting.getValue().isEmpty())
            Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(8f), setting.getValue(), textX - scrollOffset, textY, new Color(255, 255, 255, (int) (255 * animation.getValue())));

        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(8f), "|", textX - scrollOffset + textWidth, textY - 0.25f, ColorUtils.alpha(ColorUtils.pulse(Color.WHITE, 15), (int) (ColorUtils.pulse(Color.WHITE, 15).getAlpha() * animation.getValue())));

        Render2D.stopScissor(context);
    }

    private void renderReference(DrawContext context, int mouseX, int mouseY) {
        animation.update(typing);
        float ga=Math.max(0f,Math.min(1f,getGlobalAlpha()));
        boolean hovered=MathUtils.isHovered(x,y,width,height,mouseX,mouseY);
        if(hovered)Render2D.drawRoundedRect(context.getMatrices(),x,y+1f,width,height-2f,5f,
                new Color(225,241,250,(int)(8*ga)));
        Render2D.drawFont(context.getMatrices(),Fonts.REGULAR.getFont(5.1f),
                ClickGuiLanguage.marquee(ClickGuiLanguage.translate(setting.getName()), Math.max(18f, width * .40f - 8f), 5.1f, hovered),x+3f,y+6f,
                new Color(232,241,248,(int)(245*ga)));

        float pillW=Math.min(128f,Math.max(72f,width*.43f));
        float px=x+width-pillW-3f;
        Render2D.drawRoundedRect(context.getMatrices(),px,y+2f,pillW,16f,5f,
                new Color(224,240,249,(int)((typing?25:16)*ga)));
        Color accent=dev.prostovisuals.client.ui.clickgui.OneClientClickGui.referenceAccentColorAt(px+pillW*.5f,y+10f);
        Render2D.drawBorder(context.getMatrices(),px,y+2f,pillW,16f,5f,.16f,.30f,
                typing?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(100*ga))
                        :new Color(239,249,254,(int)(25*ga)));
        String value=setting.getValue()==null?"":setting.getValue();
        while(value.length()>1&&Fonts.REGULAR.getWidth(value+(typing?"|":""),4.8f)>pillW-13f)value=value.substring(1);
        Render2D.drawFont(context.getMatrices(),Fonts.REGULAR.getFont(4.8f),
                value+(typing?"|":""),px+7f,y+7f,new Color(239,247,252,(int)(245*ga)));
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
