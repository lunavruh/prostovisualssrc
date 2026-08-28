package dev.prostovisuals.client.util.notify;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.math.TimerUtils;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import lombok.Getter;

import java.awt.*;

@Getter
public class Notify implements Wrapper {

    private final NotifyIcon icon;
    private final String notify;
    private final long delay;
    private float y;
    private final Animation animation = new Animation(300, 1f, true, Easing.BOTH_SINE);
    private final TimerUtils timer = new TimerUtils();

    public Notify(NotifyIcon icon, String notify, long delay) {
        this.icon = icon;
        this.notify = notify;
        this.delay = delay;
        y = mc.getWindow().getScaledHeight() / 2f + 10;
        timer.reset();
    }

    public void render(EventRender2D e, float picunY) {
        y = animate(y, picunY);
        float width = Fonts.MEDIUM.getWidth(notify, 9f);
        float width2 = Fonts.ICONS.getWidth(icon.icon(), 8f);
        float width3 = width + width2 + 7f;
        float x = mc.getWindow().getScaledWidth() / 2f - (width3 / 2f);
        if (timer.passed(delay)) animation.update(false);
        float animA = (float) animation.getValue();
        float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);

        LiquidGlassUtil.drawLiquidGlass(
                e.getContext(),
                x - 3.5f,
                y - 3.5f,
                width3 + 7f,
                17f,
                liquidTime,
                4.0f,
                0.15f,
                0.0015f,
                4.5f,
                0.38f,
                1.025f
        );

        int tintA = (int) (28 * animA);
        Render2D.drawRoundedRect(
                e.getContext().getMatrices(),
                x - 2.5f,
                y - 2.5f,
                width3 + 5f,
                15f,
                3.5f,
                new Color(5, 7, 10, tintA)
        );
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(9f), notify, x + width2 + 4f, y - 0.5f, new Color(255, 255, 255, (int) (255 * animation.getValue())));
        Render2D.drawFont(e.getContext().getMatrices(), Fonts.ICONS.getFont(8f), icon.icon(), x + 1f, y + 1f, new Color(255, 255, 255, (int) (255 * animation.getValue())));
    }

    public float animate(float value, float target) {
        return value + (target - value) / 8f;
    }

    public boolean expired() {
        return timer.passed(delay) && animation.getValue() < 0.01f;
    }
}