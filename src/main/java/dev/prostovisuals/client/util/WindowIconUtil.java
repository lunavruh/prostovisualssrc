package dev.prostovisuals.client.util;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Replaces the native GLFW window icon with ProstoVisuals' amongus.png.
 * This changes the icon shown in the Windows taskbar/title bar while Minecraft is running.
 */
public final class WindowIconUtil {
    private static final String ICON_RESOURCE = "/assets/prostovisuals/hud/amongus.png";
    private static boolean applied;

    private WindowIconUtil() {
    }

    public static void applyAmongUsIcon(MinecraftClient client) {
        if (applied || client == null || client.getWindow() == null) {
            return;
        }

        long windowHandle = client.getWindow().getHandle();
        if (windowHandle == MemoryUtil.NULL) {
            return;
        }

        ByteBuffer encoded = null;
        ByteBuffer pixels = null;

        try (InputStream stream = WindowIconUtil.class.getResourceAsStream(ICON_RESOURCE)) {
            if (stream == null) {
                System.err.println("[ProstoVisuals] Window icon not found: " + ICON_RESOURCE);
                return;
            }

            byte[] png = stream.readAllBytes();
            encoded = MemoryUtil.memAlloc(png.length);
            encoded.put(png).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var channels = stack.mallocInt(1);

                pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
                if (pixels == null) {
                    System.err.println("[ProstoVisuals] Failed to decode window icon: " + STBImage.stbi_failure_reason());
                    return;
                }

                GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                images.position(0)
                        .width(width.get(0))
                        .height(height.get(0))
                        .pixels(pixels);
                images.position(0);

                GLFW.glfwSetWindowIcon(windowHandle, images);
                applied = true;
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[ProstoVisuals] Failed to set custom window icon: " + e.getMessage());
        } finally {
            if (pixels != null) {
                STBImage.stbi_image_free(pixels);
            }
            if (encoded != null) {
                MemoryUtil.memFree(encoded);
            }
        }
    }
}
