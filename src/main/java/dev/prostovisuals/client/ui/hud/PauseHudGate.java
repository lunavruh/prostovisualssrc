package dev.prostovisuals.client.ui.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

/**
 * Prevents liquid-glass HUD surfaces from being baked into the first pause-frame.
 * Esc can be processed before Minecraft swaps currentScreen, so checking currentScreen alone
 * leaves one custom-HUD frame behind for the pause blur to sample as dark/black capsules.
 */
public final class PauseHudGate {
    private static volatile long suppressUntilNanos;

    private PauseHudGate() {}

    public static void armForPauseTransition() {
        suppressUntilNanos = System.nanoTime() + 450_000_000L;
    }

    public static boolean shouldSuppress(MinecraftClient client) {
        if (client == null) return false;
        if (System.nanoTime() < suppressUntilNanos) return true;
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) return true;
        try {
            return client.isPaused();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
