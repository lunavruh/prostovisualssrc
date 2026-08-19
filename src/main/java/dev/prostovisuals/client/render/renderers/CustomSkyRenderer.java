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

public final class CustomSkyRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final ShaderProgramKey COSMOS = key("custom_sky_cosmos");
    private static final ShaderProgramKey WATER = key("custom_sky_water");
    private static final ShaderProgramKey CAUSTIC = key("custom_sky_caustic");
    private static final ShaderProgramKey METEOR = key("custom_sky_meteor");
    private static final ShaderProgramKey AURORA = key("custom_sky_aurora");
    private static final ShaderProgramKey STARFALL = key("custom_sky_starfall");
    private static final ShaderProgramKey BLACK_HOLE = key("custom_sky_blackhole");

    private CustomSkyRenderer() {}

    public static void render(Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        CustomSky module = prostovisuals.getInstance().getModuleManager().getModule(CustomSky.class);
        if (module == null || !module.isToggled()) return;

        ShaderProgramKey key = switch (module.getMode()) {
            case COSMOS -> COSMOS;
            case WATER -> WATER;
            case CAUSTIC -> CAUSTIC;
            case METEOR_SHOWER -> METEOR;
            case AURORA -> AURORA;
            case STARFALL -> STARFALL;
            case BLACK_HOLE -> BLACK_HOLE;
        };

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        ShaderProgram shader = RenderSystem.setShader(key);
        if (shader == null) {
            restoreState();
            return;
        }

        float width = Math.max(1, client.getWindow().getFramebufferWidth());
        float height = Math.max(1, client.getWindow().getFramebufferHeight());
        float time = (float) ((System.nanoTime() / 1_000_000_000.0) % 4096.0);
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
        set(shader, "uIntensity", module.isFluidEffect()
                ? module.getEffectIntensity() : module.getIntensity());
        set(shader, "uStars", module.getStars());
        set(shader, "uRotation", rotation);
        set(shader, "uMeteorFrequency", module.getMeteorFrequency());
        set(shader, "uAlpha", module.isFluidEffect() ? module.getEffectOpacity() : 1.0f);
        set(shader, "uSpeed", module.getEffectSpeed());
        set(shader, "uScale", 5.0f / Math.max(0.05f, module.getEffectSize()));
        if (module.getMode() == CustomSky.Mode.WATER) {
            set(shader, "uColor", 0.08f, 0.38f, 0.62f);
        } else if (module.getMode() == CustomSky.Mode.CAUSTIC) {
            set(shader, "uColor", 0.42f, 0.12f, 0.62f);
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(IDENTITY, -1.0f, -1.0f, 1.0f).color(0xFFFFFFFF);
        builder.vertex(IDENTITY, 1.0f, -1.0f, 1.0f).color(0xFFFFFFFF);
        builder.vertex(IDENTITY, 1.0f, 1.0f, 1.0f).color(0xFFFFFFFF);
        builder.vertex(IDENTITY, -1.0f, 1.0f, 1.0f).color(0xFFFFFFFF);
        BuiltBuffer built = builder.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        restoreState();
    }

    private static ShaderProgramKey key(String name) {
        return new ShaderProgramKey(Identifier.of("prostovisuals", "core/" + name),
                VertexFormats.POSITION_COLOR, Defines.EMPTY);
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
}
