package dev.prostovisuals.client.custommodels;

import java.nio.file.Path;

/** One Figura-style cosmetic/avatar available in the ProstoVisual cosmetics library. */
public record CosmeticEntry(String name, String relativePath, Path directory, Kind kind) {
    public enum Kind { MODEL, HEAD, HAT, WEAPON, WING, PET }
}
