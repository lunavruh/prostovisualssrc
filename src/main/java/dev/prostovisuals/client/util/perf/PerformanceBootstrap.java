package dev.prostovisuals.client.util.perf;

import dev.prostovisuals.prostovisuals;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Applies only zero-quality-loss frame pacing settings once per launch.
 *
 * The standalone loader can create a fresh options.txt where VSync defaults to
 * 60 Hz. That made a capable PC sit at exactly 60 FPS. We explicitly disable
 * VSync and raise vanilla's FPS limit to 260; no render distance, graphics,
 * mipmap or visual-quality setting is touched.
 */
public final class PerformanceBootstrap {
    private static boolean applied;

    private PerformanceBootstrap() {}

    public static void applyOnce(MinecraftClient client) {
        if (applied || client == null || client.options == null || client.getWindow() == null) return;
        applied = true;

        boolean changed = false;
        try {
            if (Boolean.TRUE.equals(client.options.getEnableVsync().getValue())) {
                client.options.getEnableVsync().setValue(false);
                changed = true;
            }
            Integer currentLimit = client.options.getMaxFps().getValue();
            if (currentLimit == null || currentLimit < 260) {
                client.options.getMaxFps().setValue(260);
                changed = true;
            }
            // Apply immediately to the active OpenGL context as well.  This
            // avoids one launch/fullscreen-cycle remaining locked to a 60 Hz
            // swap interval even though options.txt already says VSync=false.
            GLFW.glfwSwapInterval(0);
            if (changed) client.options.write();

            prostovisuals.LOGGER.info(
                    "[Performance] VSync={}, FPS limit={}",
                    client.options.getEnableVsync().getValue(),
                    client.options.getMaxFps().getValue()
            );
        } catch (Throwable throwable) {
            prostovisuals.LOGGER.warn("[Performance] Could not apply frame pacing defaults: {}", throwable.toString());
        }
    }
}
