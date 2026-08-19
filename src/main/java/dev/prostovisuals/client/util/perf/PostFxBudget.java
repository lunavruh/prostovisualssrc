package dev.prostovisuals.client.util.perf;

/**
 * Adaptive budget for soft ProstoVisual post effects only.
 *
 * It never changes Minecraft graphics/render distance. When sustained frame
 * time rises, only internal blur/glass/sky render targets step down slightly;
 * linear upscale hides the difference because those effects are already soft.
 * Levels change only after many consecutive frames, so FBOs are never resized
 * continuously (which itself would cause stutter).
 */
public final class PostFxBudget {
    private static long lastFrameNs;
    private static float emaFrameMs = 8.33f;
    private static float scale = 1.0f;
    private static float pendingScale = 1.0f;
    private static int pendingFrames;

    private PostFxBudget() {}

    public static void beginFrame() {
        long now = System.nanoTime();
        if (lastFrameNs == 0L) {
            lastFrameNs = now;
            return;
        }
        float ms = (now - lastFrameNs) / 1_000_000.0f;
        lastFrameNs = now;
        if (ms <= 0.0f || ms > 100.0f) return;

        emaFrameMs += (ms - emaFrameMs) * 0.055f;

        // Conservative tiers: visual world quality is untouched. Only soft
        // post-effect buffer pixels are reduced under sustained GPU pressure.
        float wanted;
        if (emaFrameMs >= 17.0f) wanted = 0.86f;
        else if (emaFrameMs >= 13.0f) wanted = 0.92f;
        else if (emaFrameMs <= 10.2f) wanted = 1.00f;
        else wanted = scale; // dead-band prevents bouncing around a threshold

        if (Math.abs(wanted - pendingScale) > 0.001f) {
            pendingScale = wanted;
            pendingFrames = 1;
        } else if (Math.abs(wanted - scale) > 0.001f) {
            pendingFrames++;
            if (pendingFrames >= 36) { // sustained for roughly 0.2-0.6 seconds
                scale = wanted;
                pendingFrames = 0;
            }
        } else {
            pendingFrames = 0;
        }
    }

    public static float getScale() {
        return scale;
    }

    public static float getAverageFrameMs() {
        return emaFrameMs;
    }

    public static void reset() {
        lastFrameNs = 0L;
        emaFrameMs = 8.33f;
        scale = pendingScale = 1.0f;
        pendingFrames = 0;
    }
}
