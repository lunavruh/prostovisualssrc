package dev.prostovisuals.client.util.animations.infinity;

import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import lombok.Getter;
import lombok.Setter;

public class InfinityAnimation {
    private float output, endpoint;
    @Setter private Easing easing;

    public InfinityAnimation(Easing easing) {
        this.easing = easing;
    }

    @Getter private Animation animation = new Animation(0, 0, false, Easing.LINEAR);

    public float animate(float value, long duration) {
        duration = Math.max(1, duration);
        output = endpoint - animation.getValue();
        endpoint = value;
        if (output != (endpoint - value)) animation = new Animation(duration, endpoint - output, false, easing);

        return output;
    }

    /** Instantly moves the animation to a value without a one-frame layout lag. */
    public void snap(float value) {
        endpoint = value;
        output = value;
        animation = new Animation(0, 0, false, easing);
    }

    public boolean finished() {
        return output == endpoint || animation.finished() || animation.finished(false);
    }

    public float getValue() {
        output = endpoint - animation.getValue();
        return output;
    }
}