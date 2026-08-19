package dev.prostovisuals.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Tiny helper for the Kimiko-style UI sound pack. */
public final class UiSoundUtil {
    private UiSoundUtil() {}

    public static void play(String id) {
        play(id, 0.62f, 1.0f);
    }

    public static void play(String id, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null) return;
        client.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvent.of(Identifier.of("prostovisuals", id)),
                pitch,
                Math.max(0.0f, Math.min(1.0f, volume))
        ));
    }
}
