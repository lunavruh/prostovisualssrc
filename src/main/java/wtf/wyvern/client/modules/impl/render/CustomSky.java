package wtf.wyvern.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import wtf.wyvern.Wyvern;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.ModeSetting;
import wtf.wyvern.client.modules.api.setting.impl.NumberSetting;
import wtf.wyvern.utility.interfaces.IMinecraft;
import wtf.wyvern.utility.render.display.base.color.ColorRGBA;
import wtf.wyvern.utility.render.display.shader.GlProgram;

@ModuleAnnotation(name = "CustomSky", category = Category.RENDER, description = "Galaxy, Energy, Plasma, Water и Caustic")
public class CustomSky extends Module implements IMinecraft {
    public static final CustomSky INSTANCE = new CustomSky();

    public final ModeSetting mode = new ModeSetting("Режим", "Galaxy", "Energy", "Plasma", "Water", "Caustic");
    public final NumberSetting speed = new NumberSetting("Скорость", 0.7f, 0.0f, 2.0f, 0.05f);
    public final NumberSetting scale = new NumberSetting("Размер", 1.0f, 0.35f, 3.0f, 0.05f);
    public final NumberSetting intensity = new NumberSetting("Интенсивность", 0.85f, 0.05f, 1.5f, 0.05f);
    public final NumberSetting stars = new NumberSetting("Звёзды", 0.85f, 0.0f, 1.5f, 0.05f);
    public final NumberSetting rotation = new NumberSetting("Поворот", 0.0f, -180.0f, 180.0f, 1.0f);
    public final NumberSetting alpha = new NumberSetting("Прозрачность", 0.70f, 0.05f, 1.0f, 0.05f);

    private static final GlProgram GALAXY = new GlProgram(Wyvern.id("skyshader/galaxy"), VertexFormats.POSITION);
    private static final GlProgram ENERGY = new GlProgram(Wyvern.id("skyshader/energy"), VertexFormats.POSITION);
    private static final GlProgram PLASMA = new GlProgram(Wyvern.id("skyshader/video_plasma"), VertexFormats.POSITION);
    private static final GlProgram WATER = new GlProgram(Wyvern.id("skyshader/water"), VertexFormats.POSITION);
    private static final GlProgram CAUSTIC = new GlProgram(Wyvern.id("skyshader/caustic"), VertexFormats.POSITION);

    private long startMillis = -1L;
    private CustomSky() {}

    @Override public void onEnable() { startMillis = System.currentTimeMillis(); super.onEnable(); }
    @Override public void onDisable() { startMillis = -1L; super.onDisable(); }

    public void renderSkyShader() {
        if (mc.player == null || mc.world == null) return;
        if (startMillis < 0L) startMillis = System.currentTimeMillis();

        GlProgram shader = switch (mode.getValue().toString()) {
            case "Energy" -> ENERGY;
            case "Plasma" -> PLASMA;
            case "Water" -> WATER;
            case "Caustic" -> CAUSTIC;
            default -> GALAXY;
        };
        if (!shader.isLoaded()) return;
        shader.use();

        float time = (System.currentTimeMillis() - startMillis) / 1000.0f;
        float width = mc.getWindow().getFramebufferWidth();
        float height = mc.getWindow().getFramebufferHeight();
        var cam = mc.gameRenderer.getCamera();
        float yaw = (float)Math.toRadians(-cam.getYaw());
        float pitch = (float)Math.toRadians(cam.getPitch());
        float fov = mc.options.getFov().getValue().floatValue();
        float rot = (float)Math.toRadians(rotation.getCurrent());
        boolean fluid = mode.is("Water") || mode.is("Caustic");

        if (fluid) {
            ColorRGBA theme = Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor();
            set(shader, "uTime", time);
            set(shader, "uResolution", width, height);
            set(shader, "uColor", theme.getRed()/255f, theme.getGreen()/255f, theme.getBlue()/255f);
            set(shader, "uAlpha", alpha.getCurrent());
            set(shader, "uSpeed", speed.getCurrent());
            set(shader, "uScale", 5.0f / Math.max(0.05f, scale.getCurrent()));
            set(shader, "uIntensity", Math.max(0.002f, intensity.getCurrent() * 0.01f));
            set(shader, "uCameraDir", yaw + rot, pitch);
            set(shader, "uFov", fov);
        } else {
            set(shader, "uTime", time);
            set(shader, "uResolution", width, height);
            set(shader, "uCameraDir", yaw, pitch);
            set(shader, "uFov", fov);
            set(shader, "uIntensity", intensity.getCurrent());
            set(shader, "uStars", stars.getCurrent());
            set(shader, "uRotation", rot);
            set(shader, "uSpeed", speed.getCurrent());
            set(shader, "uScale", scale.getCurrent());
        }

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setProjectionMatrix(new Matrix4f(), com.mojang.blaze3d.systems.ProjectionType.ORTHOGRAPHIC);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4f identity = new Matrix4f();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(identity, -1f, -1f, 1f);
        buffer.vertex(identity, 1f, -1f, 1f);
        buffer.vertex(identity, 1f, 1f, 1f);
        buffer.vertex(identity, -1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.systems.ProjectionType.PERSPECTIVE);
    }

    private static void set(GlProgram program, String name, float... values) {
        GlUniform u = program.findUniform(name);
        if (u == null) return;
        if (values.length == 1) u.set(values[0]);
        else if (values.length == 2) u.set(values[0], values[1]);
        else if (values.length == 3) u.set(values[0], values[1], values[2]);
        else if (values.length == 4) u.set(values[0], values[1], values[2], values[3]);
    }
}
