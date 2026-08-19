package dev.prostovisuals.client.util.animations;

/**
 * Frame-rate independent reversible animation.
 *
 * Uses the same snappy global timing as the v4 UI, without importing any of
 * the v4 HUD/menu/render rewrites.
 */
public class Animation {
    public static final double UI_DURATION_SCALE = 0.40;

    private long duration;
    private final Easing easing;
    private boolean forward;
    private double amplitude;
    private double progress;
    private long lastUpdateNanos;

    public Animation(long duration, double value, boolean forward, Easing easing) {
        this.duration = scaleDurationMs(duration);
        this.amplitude = value;
        this.forward = forward;
        this.easing = easing;
        this.progress = forward ? 0.0 : 1.0;
        this.lastUpdateNanos = System.nanoTime();
    }

    public static long scaleDurationMs(long requestedMs) {
        if (requestedMs <= 0L) return 0L;
        return Math.max(1L, Math.round(requestedMs * UI_DURATION_SCALE));
    }

    public void update(boolean forward) {
        advance();
        this.forward = forward;
        if (duration == 0L) progress = forward ? 1.0 : 0.0;
    }

    public void update() {
        advance();
        if (forward && progress >= 1.0) forward = false;
        else if (!forward && progress <= 0.0) forward = true;
    }

    public boolean finished(boolean direction) {
        advance();
        return direction == forward && (direction ? progress >= 0.999999 : progress <= 0.000001);
    }

    public boolean finished() {
        advance();
        return forward && progress >= 0.999999;
    }

    public float getValue() {
        advance();
        return (float) (easing.apply(clamp01(progress)) * amplitude);
    }

    public float getLinear() {
        advance();
        return (float) (clamp01(progress) * amplitude);
    }

    public float getReversedValue() {
        return (float) (amplitude - getValue());
    }

    public void reset() {
        progress = forward ? 0.0 : 1.0;
        lastUpdateNanos = System.nanoTime();
    }

    public void setDuration(long duration) {
        advance();
        this.duration = scaleDurationMs(duration);
        if (this.duration == 0L) progress = forward ? 1.0 : 0.0;
    }

    public void setValue(double value) {
        this.amplitude = value;
    }

    private void advance() {
        long now = System.nanoTime();
        long elapsed = Math.max(0L, now - lastUpdateNanos);
        lastUpdateNanos = now;

        if (duration <= 0L) {
            progress = forward ? 1.0 : 0.0;
            return;
        }

        double elapsedMs = Math.min(elapsed / 1_000_000.0, 42.0);
        progress += forward ? elapsedMs / duration : -elapsedMs / duration;
        progress = clamp01(progress);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
