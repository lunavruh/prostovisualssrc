package dev.prostovisuals.client.ui.clickgui.components.impl;

import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
// removed unused animation imports
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.ColorUtils;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.renderer.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.Locale;
import dev.prostovisuals.client.managers.ThemeManager;

public class SliderComponent extends Component {

    private final NumberSetting setting;
    // removed unused animation field
    private boolean drag;

    // Smooth animation state
    private float animatedPixel = -1f; // smoothed fill/knob x in pixels
    private float hoverAmount = 0f;    // 0..1 hover/drag highlight
    private float referenceTrackX, referenceTrackY, referenceTrackW;

    public SliderComponent(NumberSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.addHeight = () -> 3f;
        this.visible = setting::isVisible;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            renderReference(context, mouseX, mouseY);
            return;
        }
        // ранний выход, если скрыт или полностью прозрачный
        if (!visible.get() || getGlobalAlpha() <= 0.01f) return;

        // Hover и сглаживание
        boolean hovered = MathUtils.isHovered(x, y, width, height, (float) mouseX, (float) mouseY) || drag;
        hoverAmount += ((hovered ? 1f : 0f) - hoverAmount) * 0.2f;

        // Перетаскивание — обновление значения
        if (drag) {
            float value = MathHelper.clamp(
                    MathUtils.round((mouseX - x - 5f) / (width - 12f) * (setting.getMax() - setting.getMin()) + setting.getMin(), setting.getIncrement()),
                    setting.getMin(),
                    setting.getMax()
            );
            setting.setValue(value);
        }

        // Соотношение и целевая позиция ползунка
        float ratio = (float) ((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        ratio = MathHelper.clamp(ratio, 0f, 1f);
        float barWidth = width - 8f;
        float targetPixel = barWidth * ratio;
        if (animatedPixel < 0f) animatedPixel = targetPixel; // первый кадр — без анимации
        animatedPixel += (targetPixel - animatedPixel) * 0.18f; // сглаживание

        // Параметры эффектов
        float scaleValue = 1f + 0.02f * hoverAmount;
        float fadeValue = Math.max(0f, Math.min(1f, getGlobalAlpha()));

        // Масштабирование области
        float centerX = x + width / 2f;
        float centerY = y + height / 2f;
        float scaledX = centerX - (width * scaleValue) / 2f;
        float scaledY = centerY - (height * scaleValue) / 2f;
        float scaledWidth = width * scaleValue;
        float scaledHeight = height * scaleValue;

        // Фон с hover эффектом
        Color baseBg = new Color(15, 18, 24, (int) (155 * fadeValue));
        Color hoverBg = new Color(22, 26, 34, (int) (195 * fadeValue));
        Color bg = ColorUtils.fade(baseBg, hoverBg, hoverAmount);
        Render2D.drawRoundedRect(context.getMatrices(), scaledX, scaledY, scaledWidth, scaledHeight, 6f, bg);

        // Текст
        int textA = (int) Math.max(0, Math.min(255, 255 * fadeValue));
        float textOffset = hoverAmount * 1f;
        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(7.5f),
                ClickGuiLanguage.translate(setting.getName()),
                scaledX + 4f + textOffset, scaledY + 3f,
                new Color(255, 255, 255, textA));

        // Трек
        Color trackBg = new Color(23, 23, 23, (int) (100 * fadeValue));
        Render2D.drawRoundedRect(context.getMatrices(), scaledX + 4f, scaledY + 13f,
                scaledWidth - 8f, 4f, 0.5f, trackBg);

        // Заполнение трека
        Color accent = ThemeManager.getInstance().getRenderedAccentColor();
        int fillA = (int) Math.max(0, Math.min(255, 255 * fadeValue));
        Color fillColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fillA);
        // легкое усиление при hover
        if (hoverAmount > 0f) {
            fillColor = ColorUtils.fade(fillColor,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (Math.min(255, fillA * 1.2f))),
                    hoverAmount);
        }
        float fillWidth = (scaledWidth - 8f) * (animatedPixel / barWidth);
        Render2D.drawRoundedRect(context.getMatrices(), scaledX + 4f, scaledY + 13f,
                fillWidth, 4f, 0.5f, fillColor);

        // Ручка
        float knobSize = (6f + 2f * hoverAmount) * scaleValue;
        float knobX = scaledX + 1f + (animatedPixel / barWidth) * (scaledWidth - 8f) - (knobSize - 6f) / 2f;
        float knobY = scaledY + 12f - (knobSize - 6f) / 2f;
        int knobA = (int) Math.max(0, Math.min(255, 255 * fadeValue));
        Color knobColor = new Color(255, 255, 255, knobA);
        if (hoverAmount > 0f) {
            knobColor = ColorUtils.fade(knobColor,
                    new Color(255, 255, 255, (int) Math.min(255, knobA * 1.1f)),
                    hoverAmount);
        }
        Render2D.drawRoundedRect(context.getMatrices(),
                knobX, knobY,
                knobSize, knobSize,
                knobSize / 2f, knobColor);

        // Контур ручки
        int outlineA = (int) (120 * fadeValue * hoverAmount);
        if (outlineA > 0) {
            Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), outlineA);
            Render2D.drawRoundedRect(context.getMatrices(),
                    knobX - 0.5f, knobY - 0.5f,
                    knobSize + 1f, knobSize + 1f,
                    knobSize / 2f + 0.5f, outlineColor);
        }

        // Значение
        Color baseText = ThemeManager.getInstance().getRenderedTextColor();
        Color textWithAlpha = new Color(baseText.getRed(), baseText.getGreen(), baseText.getBlue(),
                Math.max(0, Math.min(255, (int) (baseText.getAlpha() * fadeValue))));
        String valueStr = String.valueOf(setting.getValue());
        float valueOffset = hoverAmount * 1f;
        Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(6f), valueStr,
                scaledX + scaledWidth - Fonts.BOLD.getWidth(valueStr, 6.5f) - 4.5f + valueOffset,
                scaledY + 5f, textWithAlpha);
    }

    private void renderReference(DrawContext context, int mouseX, int mouseY) {
        if (!visible.get() || getGlobalAlpha() <= 0.01f) return;
        float ga = Math.max(0f, Math.min(1f, getGlobalAlpha()));
        float valueW = 32f;
        float labelW = Math.min(78f, Math.max(58f, width * .28f));
        referenceTrackX = x + labelW;
        referenceTrackY = y + 9f;
        referenceTrackW = Math.max(18f, width - labelW - valueW - 13f);

        if (drag) {
            float ratio = MathHelper.clamp((mouseX - referenceTrackX) / referenceTrackW, 0f, 1f);
            float value = MathHelper.clamp(
                    MathUtils.round(ratio * (setting.getMax() - setting.getMin()) + setting.getMin(), setting.getIncrement()),
                    setting.getMin(), setting.getMax());
            setting.setValue(value);
        }

        boolean hovered = MathUtils.isHovered(x, y, width, height, mouseX, mouseY) || drag;
        hoverAmount += ((hovered ? 1f : 0f) - hoverAmount) * .22f;
        if (hovered) {
            Render2D.drawRoundedRect(context.getMatrices(), x, y + 1f, width, height - 2f, 5f,
                    new Color(225,241,250,(int)(8*ga)));
        }

        String label = ClickGuiLanguage.marquee(ClickGuiLanguage.translate(setting.getName()),
                Math.max(18f, labelW - 7f), 5.1f, hovered);
        Render2D.drawFont(context.getMatrices(), Fonts.REGULAR.getFont(5.1f),
                label, x + 3f, y + 6f,
                new Color(233,242,249,(int)(245*ga)));

        float ratio = MathHelper.clamp((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()), 0f, 1f);
        float target = referenceTrackW * ratio;
        if (animatedPixel < 0f || animatedPixel > referenceTrackW + 2f) animatedPixel = target;
        animatedPixel += (target - animatedPixel) * .22f;
        Color accent = dev.prostovisuals.client.ui.clickgui.OneClientClickGui.referenceAccentColorAt(referenceTrackX + referenceTrackW * .5f, referenceTrackY + 1.1f);
        Render2D.drawRoundedRect(context.getMatrices(), referenceTrackX, referenceTrackY,
                referenceTrackW, 2.2f, 1.1f, new Color(207,229,241,(int)(39*ga)));
        Render2D.drawRoundedRect(context.getMatrices(), referenceTrackX, referenceTrackY,
                Math.max(1f, animatedPixel), 2.2f, 1.1f,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(238*ga)));
        float knob = 6.3f + hoverAmount * 1.1f;
        float knobX = referenceTrackX + animatedPixel - knob * .5f;
        float knobY = referenceTrackY + 1.1f - knob * .5f;
        Render2D.drawRoundedRect(context.getMatrices(), knobX, knobY, knob, knob, knob*.5f,
                new Color(236,248,255,(int)(248*ga)));
        Render2D.drawBorder(context.getMatrices(), knobX, knobY, knob, knob, knob*.5f, .16f, .30f,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(190*ga)));

        float valueX = x + width - valueW - 3f;
        Render2D.drawRoundedRect(context.getMatrices(), valueX, y + 2f, valueW, 16f, 5f,
                new Color(224,240,249,(int)(16*ga)));
        Render2D.drawBorder(context.getMatrices(), valueX, y + 2f, valueW, 16f, 5f, .16f, .30f,
                new Color(239,249,254,(int)(25*ga)));
        centeredValue(context, displayValue(), valueX, y + 7f, valueW, ga);
    }

    private void centeredValue(DrawContext context, String text, float x, float y, float w, float alpha) {
        float tw = Fonts.MEDIUM.getWidth(text, 4.8f);
        Render2D.drawFont(context.getMatrices(), Fonts.MEDIUM.getFont(4.8f), text,
                x + (w - tw) * .5f, y, new Color(240,247,252,(int)(245*alpha)));
    }

    private String displayValue() {
        String value = String.format(Locale.ROOT, "%.2f", setting.getValue());
        while (value.contains(".") && value.endsWith("0")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
            if (button == 0 && MathUtils.isHovered(referenceTrackX - 3f, referenceTrackY - 5f,
                    referenceTrackW + 6f, 12f, (float)mouseX, (float)mouseY)) drag = true;
            return;
        }
        if (button == 0 && MathUtils.isHovered(x + 4f, y + 12f, width - 8f, 6f, (float) mouseX, (float) mouseY)) {
            drag = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) drag = false;
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
