package dev.prostovisuals.client.render.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.modules.impl.render.CustomSky;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.awt.Color;

public final class CustomSkyRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static int lastFramebufferWidth = -1;
    private static int lastFramebufferHeight = -1;
    private static long framebufferChangedAtNs = System.nanoTime();

    private static final ShaderProgramKey COSMOS = key("custom_sky_cosmos");
    private static final ShaderProgramKey WATER = key("custom_sky_water");
    private static final ShaderProgramKey CAUSTIC = key("custom_sky_caustic");
    private static final ShaderProgramKey METEOR = key("custom_sky_meteor");
    private static final ShaderProgramKey AURORA = key("custom_sky_aurora");
    private static final ShaderProgramKey STARFALL = key("custom_sky_starfall");
    // Keep all custom skies inside one ProstoVisual pipeline/vertex contract. Mixing
    // Wyvern GlProgram resources with RenderSystem ShaderProgramKey was the source of
    // world-load crashes on Energy/Plasma.
    private static final ShaderProgramKey GALAXY = key("custom_sky_galaxy");
    private static final ShaderProgramKey ENERGY = key("custom_sky_energy");
    private static final ShaderProgramKey PLASMA = key("custom_sky_plasma");

    private CustomSkyRenderer() {}

    /**
     * F11/window-mode changes can swap the main framebuffer between frame-graph passes.
     * Readiness is time based rather than call-count based because both the sky and cloud
     * mixins query it in the same frame.  A call counter made F11 settle non-deterministic.
     */
    public static boolean framebufferReady() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return false;
        int w = Math.max(1, client.getWindow().getFramebufferWidth());
        int h = Math.max(1, client.getWindow().getFramebufferHeight());
        long now = System.nanoTime();
        if (w != lastFramebufferWidth || h != lastFramebufferHeight) {
            lastFramebufferWidth = w;
            lastFramebufferHeight = h;
            framebufferChangedAtNs = now;
            return false;
        }
        // Give GLFW/Minecraft's frame graph ~3 frames at 60 Hz to settle after resize.
        return now - framebufferChangedAtNs >= 55_000_000L;
    }

    public static void render(Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !framebufferReady()) return;

        CustomSky module = prostovisuals.getInstance().getModuleManager().getModule(CustomSky.class);
        if (module == null || !module.isToggled()) return;

        ShaderProgramKey key = switch (module.getMode()) {
            case GALAXY -> GALAXY;
            case ENERGY -> ENERGY;
            case PLASMA -> PLASMA;
            case COSMOS -> COSMOS;
            case WATER -> WATER;
            case CAUSTIC -> CAUSTIC;
            case METEOR_SHOWER -> METEOR;
            case AURORA -> AURORA;
            case STARFALL -> STARFALL;
        };

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram shader;
        try {
            shader = RenderSystem.setShader(key);
        } catch (Throwable primaryFailure) {
            // Energy/Plasma are optional visual modes. A shader compile/resource problem must
            // never kick the player from a world; fall back to the lightweight Cosmos pass.
            try {
                shader = RenderSystem.setShader(COSMOS);
            } catch (Throwable fallbackFailure) {
                restoreState();
                return;
            }
        }
        if (shader == null) {
            restoreState();
            return;
        }

        float width = Math.max(1, client.getWindow().getFramebufferWidth());
        float height = Math.max(1, client.getWindow().getFramebufferHeight());
        // Fullscreen (F11) can resize the framebuffer between world passes. Always refresh the
        // viewport from the live framebuffer before drawing the screen-space sky quad.
        RenderSystem.viewport(0, 0, (int) width, (int) height);
        float rawTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 4096.0);
        // One authoritative speed path for every sky. Some legacy shaders did not expose
        // uSpeed at all, which made the GUI slider appear decorative.
        float time = rawTime * Math.max(0.0f, module.getShaderSpeed());
        float worldTime = Math.floorMod(client.world.getTimeOfDay(), 24000L) / 24000.0f;
        float yaw = (float) Math.toRadians(-camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());
        float fov = client.options.getFov().getValue().floatValue();
        float rotation = (float) Math.toRadians(module.getRotation());
        module.captureAnchor(yaw, pitch);

        set(shader, "uTime", time);
        set(shader, "uResolution", width, height);
        set(shader, "uCameraDir", yaw + (module.isFluidEffect() ? rotation : 0.0f), pitch);
        set(shader, "uAnchorDir", module.getAnchorYaw(), module.getAnchorPitch());
        set(shader, "uFov", fov);
        set(shader, "uWorldTime", worldTime);
        set(shader, "uIntensity", module.getIntensity());
        set(shader, "uStars", module.getStars());
        set(shader, "uRotation", rotation);
        set(shader, "uMeteorFrequency", module.getMeteorFrequency());
        set(shader, "uAlpha", module.isFluidEffect() ? module.getEffectOpacity() : 1.0f);
        set(shader, "uSpeed", 1.0f);
        set(shader, "uScale", module.isVideoSky()
                ? module.getShaderScale() : 5.0f / Math.max(0.05f, module.getEffectSize()));
        if (module.getMode() == CustomSky.Mode.WATER) {
            setColor(shader, "uColor", module.getWaterColor());
        } else if (module.getMode() == CustomSky.Mode.CAUSTIC) {
            setColor(shader, "uColor", module.getCausticColor());
        } else if (module.getMode() == CustomSky.Mode.AURORA) {
            setColor(shader, "uColor", module.getAuroraColor());
        }

        // custom_sky.vsh writes clip-space Position directly, so it must not inherit or
        // replace the world projection at all. Keeping projection/model-view untouched fixes
        // F11 tearing and broken slices during fast camera movement/resizes.
        try {
            RenderSystem.disableScissor();
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
            builder.vertex(IDENTITY, -1.0f, -1.0f, 0.0f);
            builder.vertex(IDENTITY,  1.0f, -1.0f, 0.0f);
            builder.vertex(IDENTITY,  1.0f,  1.0f, 0.0f);
            builder.vertex(IDENTITY, -1.0f,  1.0f, 0.0f);
            BuiltBuffer built = builder.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        } catch (Throwable ignored) {
        } finally {
            restoreState();
        }
    }

    private static ShaderProgramKey key(String name) {
        return new ShaderProgramKey(Identifier.of("prostovisuals", "core/" + name),
                VertexFormats.POSITION, Defines.EMPTY);
    }


    private static void restoreState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void set(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static void set(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }

    private static void set(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z);
    }

    private static void setColor(ShaderProgram shader, String name, Color color) {
        if (color == null) return;
        set(shader, name, color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);
    }
}
