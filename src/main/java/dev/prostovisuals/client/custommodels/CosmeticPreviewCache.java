package dev.prostovisuals.client.custommodels;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Small lazy cache for avatar.png cards in the existing ProstoVisual model menu. */
public final class CosmeticPreviewCache {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private CosmeticPreviewCache() {}

    public static Identifier get(CosmeticEntry entry) {
        if (entry == null) return null;
        return CACHE.computeIfAbsent(entry.relativePath(), k -> load(entry));
    }

    private static Identifier load(CosmeticEntry entry) {
        Path image = findAvatarPng(entry.directory());
        if (image == null) return null;
        try (InputStream in = Files.newInputStream(image)) {
            NativeImage nativeImage = NativeImage.read(in);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            texture.setFilter(true, false);
            Identifier id = Identifier.of("prostovisuals", "cosmetic_preview/" + Integer.toHexString(entry.relativePath().hashCode()));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            return id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path findAvatarPng(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).equals("avatar.png"))
                    .findFirst().orElse(null);
        } catch (Throwable ignored) { return null; }
    }
}
