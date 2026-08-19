package dev.prostovisuals.client.util.renderer.fonts;

import dev.prostovisuals.client.render.msdf.MsdfFont;

public final class Font {
    private static final int INSTANCE_CACHE_SIZE = 32;
    private final MsdfFont font;
    private final int[] cachedSizeBits = new int[INSTANCE_CACHE_SIZE];
    private final Instance[] cachedInstances = new Instance[INSTANCE_CACHE_SIZE];
    private int nextCacheSlot;

    public Font(MsdfFont font) {
        this.font = font;
    }

    public MsdfFont font() {
        return font;
    }

    public Instance getFont(float size) {
        int bits = Float.floatToIntBits(size);
        for (int i = 0; i < INSTANCE_CACHE_SIZE; i++) {
            if (cachedInstances[i] != null && cachedSizeBits[i] == bits) return cachedInstances[i];
        }
        Instance instance = new Instance(font, size);
        cachedSizeBits[nextCacheSlot] = bits;
        cachedInstances[nextCacheSlot] = instance;
        nextCacheSlot = (nextCacheSlot + 1) % INSTANCE_CACHE_SIZE;
        return instance;
    }

    public float getWidth(String text, float size) {
        return font.getWidth(text, size);
    }

    public float getHeight(float size) {
        return font.getHeight(size);
    }
}
