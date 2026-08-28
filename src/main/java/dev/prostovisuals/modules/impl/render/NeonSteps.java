package dev.prostovisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

public final class NeonSteps extends Module {
    private static final int SEGMENTS = 28;
    private static final int MAX_FLASHES = 72;

    private final EnumSetting<StepMode> mode = new EnumSetting<>("Mode", StepMode.FOOTPRINT);
    private final NumberSetting size = new NumberSetting("Size", 0.34f, 0.15f, 0.75f, 0.01f);
    private final NumberSetting duration = new NumberSetting("Duration", 1200.0f, 300.0f, 4000.0f, 50.0f);
    private final NumberSetting intensity = new NumberSetting("Intensity", 1.0f, 0.25f, 2.0f, 0.05f);
    private final VisualColorSettings visualColor = new VisualColorSettings();

    private final List<StepFlash> flashes = new ArrayList<>();
    private Vec3d lastPlayerPos;
    private double walkedSinceStep;
    private boolean nextStepLeft = true;
    private boolean wasTouchingSurface;
    private long lastStepAt;

    public NeonSteps() {
        super("NeonSteps", Category.Render, "Neon flashes at the real left and right foot contacts");
    }

    @Override
    public void onEnable() {
        flashes.clear();
        lastPlayerPos = null;
        walkedSinceStep = 0.0;
        nextStepLeft = true;
        wasTouchingSurface = false;
        lastStepAt = 0L;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        flashes.clear();
        lastPlayerPos = null;
        walkedSinceStep = 0.0;
        wasTouchingSurface = false;
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (fullNullCheck()) return;

        long now = System.currentTimeMillis();
        for (int i = flashes.size() - 1; i >= 0; i--) {
            StepFlash flash = flashes.get(i);
            if (now - flash.createdAt > flash.lifeMs) flashes.remove(i);
        }

        Vec3d currentPos = mc.player.getPos();
        if (lastPlayerPos == null) {
            lastPlayerPos = currentPos;
            wasTouchingSurface = mc.player.isOnGround();
            return;
        }

        double moved = Math.hypot(currentPos.x - lastPlayerPos.x, currentPos.z - lastPlayerPos.z);
        lastPlayerPos = currentPos;

        if (moved > 1.25) {
            walkedSinceStep = 0.0;
            return;
        }

        double centerSurfaceY = findSurfaceY(currentPos.x, currentPos.z, currentPos.y);
        boolean touchingSurface = !Double.isNaN(centerSurfaceY)
                && Math.abs(currentPos.y - centerSurfaceY) < 0.42
                && mc.player.getVelocity().y < 0.14;
        boolean grounded = mc.player.isOnGround() || touchingSurface;
        if (!grounded) {
            wasTouchingSurface = false;
            return;
        }

        if (!wasTouchingSurface) {
            spawnStep(currentPos, nextStepLeft, now);
            nextStepLeft = !nextStepLeft;
            walkedSinceStep = 0.0;
            wasTouchingSurface = true;
            return;
        }
        wasTouchingSurface = true;

        if (moved < 0.0015) return;
        walkedSinceStep += moved;

        double stepDistance = mc.player.isSneaking() ? 0.20 : mc.player.isSprinting() ? 0.38 : 0.30;
        int emitted = 0;
        while (walkedSinceStep >= stepDistance && emitted++ < 2) {
            if (now - lastStepAt >= 40L) spawnStep(currentPos, nextStepLeft, now);
            nextStepLeft = !nextStepLeft;
            walkedSinceStep -= stepDistance;
        }
    }

    private void spawnStep(Vec3d playerPos, boolean left, long now) {
        float yaw = mc.player.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        Vec3d side = new Vec3d(Math.cos(yaw), 0.0, Math.sin(yaw)).multiply(left ? -0.18 : 0.18);
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw)).multiply(0.10);
        Vec3d foot = playerPos.add(side).add(forward);
        double surfaceY = findSurfaceY(foot.x, foot.z, mc.player.getY());
        if (Double.isNaN(surfaceY)) {
            foot = playerPos.add(forward);
            surfaceY = findSurfaceY(foot.x, foot.z, mc.player.getY());
        }
        if (!Double.isNaN(surfaceY)) {
            if (flashes.size() >= MAX_FLASHES) flashes.remove(0);
            flashes.add(new StepFlash(new Vec3d(foot.x, surfaceY + 0.012, foot.z), now,
                    Math.max(1L, duration.getValue().longValue()), yaw, left));
            lastStepAt = now;
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck() || flashes.isEmpty()) return;

        long now = System.currentTimeMillis();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        Matrix4f matrix = event.getMatrices().peek().getPositionMatrix();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Color selectedColor = visualColor.resolve();
        float selectedRed = selectedColor.getRed() / 255.0f;
        float selectedGreen = selectedColor.getGreen() / 255.0f;
        float selectedBlue = selectedColor.getBlue() / 255.0f;
        for (StepFlash flash : flashes) {
            float t = MathHelper.clamp((now - flash.createdAt) / (float) flash.lifeMs, 0.0f, 1.0f);
            float fade = (1.0f - t) * (1.0f - t);
            float radius = size.getValue() * (0.78f + 0.42f * (1.0f - (1.0f - t) * (1.0f - t)));
            float power = intensity.getValue() * fade;
            Vec3d center = flash.pos.subtract(camera);

            switch (mode.getValue()) {
                case FOOTPRINT -> addFootprint(builder, matrix, center, radius, flash.yaw, flash.left,
                        power, selectedRed, selectedGreen, selectedBlue);
                case WINGS -> addWings(builder, matrix, center, radius, flash.yaw,
                        t, selectedRed, selectedGreen, selectedBlue, 0.94f * power);
                case SLASH -> addSlashes(builder, matrix, center, radius, flash.yaw,
                        t, selectedRed, selectedGreen, selectedBlue, 0.95f * power);
                case BLOSSOM -> addBlossom(builder, matrix, center, radius, flash.yaw,
                        t, selectedRed, selectedGreen, selectedBlue, 0.92f * power);
            }
        }

        BuiltBuffer built = builder.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addDisc(BufferBuilder builder, Matrix4f matrix, Vec3d center, float radius,
                                float red, float green, float blue, float alpha) {
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = MathHelper.TAU * i / SEGMENTS;
            double a1 = MathHelper.TAU * (i + 1) / SEGMENTS;
            builder.vertex(matrix, (float) center.x, (float) center.y, (float) center.z).color(red, green, blue, alpha);
            builder.vertex(matrix, (float) (center.x + Math.cos(a0) * radius), (float) center.y,
                    (float) (center.z + Math.sin(a0) * radius)).color(red, green, blue, 0.0f);
            builder.vertex(matrix, (float) (center.x + Math.cos(a1) * radius), (float) center.y,
                    (float) (center.z + Math.sin(a1) * radius)).color(red, green, blue, 0.0f);
        }
    }

    private static void addFootprint(BufferBuilder builder, Matrix4f matrix, Vec3d center, float radius,
                                     float yaw, boolean left, float power,
                                     float red, float green, float blue) {
        float sideOffset = left ? -radius * 0.08f : radius * 0.08f;
        Vec3d shifted = orientedOffset(center, yaw, sideOffset, 0.0f);
        addEllipse(builder, matrix, shifted, radius * 0.48f, radius,
                yaw, red, green, blue, 0.40f * power);
        addEllipse(builder, matrix, shifted.add(0.0, 0.002, 0.0), radius * 0.19f, radius * 0.42f,
                yaw, Math.min(1.0f, red * 1.22f), Math.min(1.0f, green * 1.22f),
                Math.min(1.0f, blue * 1.22f), 0.90f * power);
        for (int toe = -1; toe <= 1; toe++) {
            Vec3d toeCenter = orientedOffset(shifted.add(0.0, 0.004, 0.0), yaw,
                    toe * radius * 0.20f, radius * 0.76f + Math.abs(toe) * radius * 0.04f);
            addDisc(builder, matrix, toeCenter, radius * (toe == 0 ? 0.11f : 0.085f),
                    Math.min(1.0f, red * 1.16f), Math.min(1.0f, green * 1.16f),
                    Math.min(1.0f, blue * 1.16f), 0.82f * power);
        }
    }

    private static Vec3d orientedOffset(Vec3d center, float yaw, float side, float forward) {
        double x = Math.cos(yaw) * side - Math.sin(yaw) * forward;
        double z = Math.sin(yaw) * side + Math.cos(yaw) * forward;
        return center.add(x, 0.0, z);
    }

    private static void addEllipse(BufferBuilder builder, Matrix4f matrix, Vec3d center,
                                   float sideRadius, float forwardRadius, float yaw,
                                   float red, float green, float blue, float alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = MathHelper.TAU * i / SEGMENTS;
            double a1 = MathHelper.TAU * (i + 1) / SEGMENTS;
            Vec3d p0 = orientedOffset(center, yaw,
                    (float) Math.cos(a0) * sideRadius, (float) Math.sin(a0) * forwardRadius);
            Vec3d p1 = orientedOffset(center, yaw,
                    (float) Math.cos(a1) * sideRadius, (float) Math.sin(a1) * forwardRadius);
            builder.vertex(matrix, (float) center.x, (float) center.y, (float) center.z)
                    .color(red, green, blue, alpha);
            builder.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z)
                    .color(red, green, blue, 0.0f);
            builder.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z)
                    .color(red, green, blue, 0.0f);
        }
    }

    private static void addRing(BufferBuilder builder, Matrix4f matrix, Vec3d center,
                                float radius, float width,
                                float red, float green, float blue, float alpha) {
        addPolygonRing(builder, matrix, center, radius, width, SEGMENTS, 0.0f,
                red, green, blue, alpha);
    }

    private static void addPolygonRing(BufferBuilder builder, Matrix4f matrix, Vec3d center,
                                       float radius, float width, int sides, float rotation,
                                       float red, float green, float blue, float alpha) {
        float inner = Math.max(0.0f, radius - width);
        float outer = radius + width;
        for (int i = 0; i < sides; i++) {
            double a0 = rotation + MathHelper.TAU * i / sides;
            double a1 = rotation + MathHelper.TAU * (i + 1) / sides;
            float x0i = (float) (center.x + Math.cos(a0) * inner);
            float z0i = (float) (center.z + Math.sin(a0) * inner);
            float x0o = (float) (center.x + Math.cos(a0) * outer);
            float z0o = (float) (center.z + Math.sin(a0) * outer);
            float x1i = (float) (center.x + Math.cos(a1) * inner);
            float z1i = (float) (center.z + Math.sin(a1) * inner);
            float x1o = (float) (center.x + Math.cos(a1) * outer);
            float z1o = (float) (center.z + Math.sin(a1) * outer);
            float y = (float) center.y;

            builder.vertex(matrix, x0i, y, z0i).color(red, green, blue, alpha);
            builder.vertex(matrix, x0o, y, z0o).color(red, green, blue, alpha);
            builder.vertex(matrix, x1o, y, z1o).color(red, green, blue, alpha);
            builder.vertex(matrix, x0i, y, z0i).color(red, green, blue, alpha);
            builder.vertex(matrix, x1o, y, z1o).color(red, green, blue, alpha);
            builder.vertex(matrix, x1i, y, z1i).color(red, green, blue, alpha);
        }
    }

    private static void addSparks(BufferBuilder builder, Matrix4f matrix, Vec3d center, float radius,
                                  float rotation, float progress,
                                  float red, float green, float blue, float alpha) {
        float start = radius * (0.22f + progress * 0.75f);
        float end = start + radius * (0.58f + progress * 0.28f);
        for (int spark = 0; spark < 8; spark++) {
            double angle = rotation + MathHelper.TAU * spark / 8.0 + Math.sin(spark * 3.7) * 0.12;
            double spread = 0.075;
            float x0 = (float) (center.x + Math.cos(angle - spread) * start);
            float z0 = (float) (center.z + Math.sin(angle - spread) * start);
            float x1 = (float) (center.x + Math.cos(angle + spread) * start);
            float z1 = (float) (center.z + Math.sin(angle + spread) * start);
            float xt = (float) (center.x + Math.cos(angle) * end);
            float zt = (float) (center.z + Math.sin(angle) * end);
            float y = (float) center.y;
            builder.vertex(matrix, x0, y, z0).color(red, green, blue, alpha * 0.34f);
            builder.vertex(matrix, x1, y, z1).color(red, green, blue, alpha * 0.34f);
            builder.vertex(matrix, xt, y, zt).color(red, green, blue, 0.0f);
        }
    }

    private static void addWings(BufferBuilder builder, Matrix4f matrix, Vec3d center, float radius,
                                 float yaw, float progress,
                                 float red, float green, float blue, float alpha) {
        float spread = radius * (0.72f + progress * 0.62f);
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            int sideSign = sideIndex == 0 ? -1 : 1;
            for (int feather = 0; feather < 4; feather++) {
                float side = sideSign * spread * (0.45f + feather * 0.22f);
                float forward = radius * (-0.35f + feather * 0.27f);
                Vec3d root = orientedOffset(center, yaw,
                        sideSign * radius * 0.08f, -radius * 0.18f);
                Vec3d tip = orientedOffset(center, yaw, side, forward);
                Vec3d inner = orientedOffset(center, yaw,
                        side * 0.56f, forward - radius * (0.24f + feather * 0.035f));
                float featherAlpha = alpha * (1.0f - feather * 0.11f);
                putTriangle(builder, matrix, root.add(0.0, feather * 0.0015, 0.0),
                        tip.add(0.0, feather * 0.0015, 0.0),
                        inner.add(0.0, feather * 0.0015, 0.0),
                        red, green, blue, featherAlpha);
                putTopSegment(builder, matrix, root, tip, radius * 0.035f,
                        0.90f, 1.0f, 1.0f, featherAlpha);
            }
        }
    }

    private static void addSlashes(BufferBuilder builder, Matrix4f matrix, Vec3d center, float radius,
                                   float yaw, float progress,
                                   float red, float green, float blue, float alpha) {
        float slashRadius = radius * (0.74f + progress * 0.92f);
        float width = radius * (0.13f - progress * 0.035f);
        addArc(builder, matrix, center, slashRadius, width,
                yaw - 2.42f, yaw + 0.72f, 18,
                red, green, blue, alpha);
        addArc(builder, matrix, center.add(0.0, 0.003, 0.0), slashRadius * 0.82f, width * 0.78f,
                yaw + 0.76f, yaw + 3.90f, 18,
                0.94f, 0.38f, 1.0f, alpha * 0.88f);
    }

    private static void addBlossom(BufferBuilder builder, Matrix4f matrix, Vec3d center, float radius,
                                   float yaw, float progress,
                                   float red, float green, float blue, float alpha) {
        float bloom = radius * (0.28f + progress * 0.70f);
        float rotation = yaw + progress * 0.72f;
        for (int petal = 0; petal < 6; petal++) {
            float angle = rotation + MathHelper.TAU * petal / 6.0f;
            Vec3d petalCenter = center.add(Math.cos(angle) * bloom, petal * 0.0012,
                    Math.sin(angle) * bloom);
            float alternating = (petal & 1) == 0 ? 1.0f : 0.78f;
            addEllipse(builder, matrix, petalCenter,
                    radius * 0.16f * alternating, radius * 0.39f * alternating,
                    angle - MathHelper.HALF_PI,
                    red, green, blue, alpha * (0.72f + alternating * 0.22f));
        }
        addDisc(builder, matrix, center.add(0.0, 0.009, 0.0), radius * 0.16f,
                1.0f, 0.84f, 0.96f, alpha);
    }

    private static void addArc(BufferBuilder builder, Matrix4f matrix, Vec3d center,
                               float radius, float width, float startAngle, float endAngle, int segments,
                               float red, float green, float blue, float alpha) {
        float inner = Math.max(0.0f, radius - width);
        float outer = radius + width;
        for (int segment = 0; segment < segments; segment++) {
            float t0 = segment / (float) segments;
            float t1 = (segment + 1) / (float) segments;
            float a0 = MathHelper.lerp(t0, startAngle, endAngle);
            float a1 = MathHelper.lerp(t1, startAngle, endAngle);
            float segmentAlpha = alpha * (0.20f + 0.80f * t1);
            float x0i = (float) (center.x + Math.cos(a0) * inner);
            float z0i = (float) (center.z + Math.sin(a0) * inner);
            float x0o = (float) (center.x + Math.cos(a0) * outer);
            float z0o = (float) (center.z + Math.sin(a0) * outer);
            float x1i = (float) (center.x + Math.cos(a1) * inner);
            float z1i = (float) (center.z + Math.sin(a1) * inner);
            float x1o = (float) (center.x + Math.cos(a1) * outer);
            float z1o = (float) (center.z + Math.sin(a1) * outer);
            float y = (float) center.y;
            builder.vertex(matrix, x0i, y, z0i).color(red, green, blue, segmentAlpha * 0.58f);
            builder.vertex(matrix, x0o, y, z0o).color(red, green, blue, segmentAlpha);
            builder.vertex(matrix, x1o, y, z1o).color(red, green, blue, segmentAlpha);
            builder.vertex(matrix, x0i, y, z0i).color(red, green, blue, segmentAlpha * 0.58f);
            builder.vertex(matrix, x1o, y, z1o).color(red, green, blue, segmentAlpha);
            builder.vertex(matrix, x1i, y, z1i).color(red, green, blue, segmentAlpha * 0.58f);
        }
    }

    private static void putTriangle(BufferBuilder builder, Matrix4f matrix, Vec3d a, Vec3d b, Vec3d c,
                                    float red, float green, float blue, float alpha) {
        builder.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(red, green, blue, alpha * 0.20f);
        builder.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(0.92f, 1.0f, 1.0f, alpha);
        builder.vertex(matrix, (float) c.x, (float) c.y, (float) c.z).color(red, green, blue, alpha * 0.48f);
    }

    private static void putTopSegment(BufferBuilder builder, Matrix4f matrix, Vec3d from, Vec3d to,
                                      float width, float red, float green, float blue, float alpha) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001) return;
        float px = (float) (-dz / length * width);
        float pz = (float) (dx / length * width);
        builder.vertex(matrix, (float) from.x + px, (float) from.y, (float) from.z + pz).color(red, green, blue, alpha);
        builder.vertex(matrix, (float) to.x + px, (float) to.y, (float) to.z + pz).color(red, green, blue, alpha);
        builder.vertex(matrix, (float) to.x - px, (float) to.y, (float) to.z - pz).color(red, green, blue, alpha);
        builder.vertex(matrix, (float) from.x + px, (float) from.y, (float) from.z + pz).color(red, green, blue, alpha);
        builder.vertex(matrix, (float) to.x - px, (float) to.y, (float) to.z - pz).color(red, green, blue, alpha);
        builder.vertex(matrix, (float) from.x - px, (float) from.y, (float) from.z - pz).color(red, green, blue, alpha);
    }

    private double findSurfaceY(double x, double z, double hintY) {
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        int startY = MathHelper.floor(hintY) + 1;
        for (int y = startY; y >= startY - 7; y--) {
            BlockPos pos = new BlockPos(blockX, y, blockZ);
            BlockState state = mc.world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(mc.world, pos);
            if (!shape.isEmpty()) return y + shape.getMax(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    public enum StepMode implements Nameable {
        FOOTPRINT("Footprint"),
        WINGS("Wings"),
        SLASH("Slash"),
        BLOSSOM("Blossom");

        private final String name;

        StepMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private record StepFlash(Vec3d pos, long createdAt, long lifeMs, float yaw, boolean left) {}
}
