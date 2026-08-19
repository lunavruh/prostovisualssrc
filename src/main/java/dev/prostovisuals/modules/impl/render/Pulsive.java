package dev.prostovisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;

public final class Pulsive extends Module {
    private static final int MAX_WAVES = 8;
    private static final float SURFACE_LIFT = 0.018f;
    private static final int MAX_SURFACE_RISE = 64;
    private static final int MAX_SURFACE_DROP = 48;
    private static final float VERTICAL_PATH_COST = 0.10f;
    private static final int[][] SURFACE_NEIGHBORS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final EnumSetting<WaveMode> mode = new EnumSetting<>("Mode", WaveMode.NEON);
    private final BooleanSetting self = new BooleanSetting("Self", true);
    private final BooleanSetting players = new BooleanSetting("Players", true);
    private final NumberSetting radius = new NumberSetting("Radius", 8.0f, 2.0f, 14.0f, 0.5f);
    private final NumberSetting duration = new NumberSetting("Duration", 500.0f, 250.0f, 1200.0f, 25.0f);
    private final NumberSetting intensity = new NumberSetting("Intensity / Glow", 1.0f, 0.2f, 2.0f, 0.05f);
    private final BooleanSetting dimming = new BooleanSetting("Landing Dimming", true);
    private final BooleanSetting sound = new BooleanSetting("Sound", true);

    private final Map<UUID, LandingState> landingStates = new HashMap<>();
    private final List<Wave> waves = new ArrayList<>();
    private final Set<UUID> presentPlayers = new HashSet<>();

    public Pulsive() {
        super("Pulsive", Category.Render, "Terrain-following neon wave on real landings");
    }

    @Override
    public void onEnable() {
        landingStates.clear();
        waves.clear();
        presentPlayers.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        landingStates.clear();
        waves.clear();
        presentPlayers.clear();
        super.onDisable();
    }

    @EventHandler
    public void onTick(EventTick event) {
        if (fullNullCheck()) return;

        long now = System.currentTimeMillis();
        for (int i = waves.size() - 1; i >= 0; i--) {
            Wave wave = waves.get(i);
            if (now - wave.createdAt > wave.lifeMs) waves.remove(i);
        }

        presentPlayers.clear();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            presentPlayers.add(player.getUuid());
            boolean enabledForPlayer = player == mc.player ? self.getValue() : players.getValue();
            updateLanding(player, enabledForPlayer, now);
        }
        Iterator<UUID> stateIterator = landingStates.keySet().iterator();
        while (stateIterator.hasNext()) {
            if (!presentPlayers.contains(stateIterator.next())) stateIterator.remove();
        }
    }

    private void updateLanding(PlayerEntity player, boolean enabledForPlayer, long now) {
        LandingState state = landingStates.computeIfAbsent(player.getUuid(), uuid ->
                new LandingState(player.isOnGround(), player.getY()));
        boolean onGround = player.isOnGround();

        if (!onGround) {
            if (state.wasOnGround) {
                state.airborneTicks = 1;
                state.takeoffY = player.getY();
                state.highestY = player.getY();
                state.lowestVerticalSpeed = (float) player.getVelocity().y;
                state.maxFallDistance = player.fallDistance;
            } else {
                state.airborneTicks++;
                state.highestY = Math.max(state.highestY, player.getY());
                state.lowestVerticalSpeed = Math.min(state.lowestVerticalSpeed, (float) player.getVelocity().y);
            }
            state.maxFallDistance = Math.max(state.maxFallDistance, player.fallDistance);
        }

        if (!state.wasOnGround && onGround) {
            double jumpHeight = state.highestY - state.takeoffY;
            boolean realLanding = state.airborneTicks >= 2
                    && (jumpHeight > 0.16 || state.lowestVerticalSpeed < -0.10f || state.maxFallDistance > 0.20f);
            if (enabledForPlayer && realLanding) {
                Vec3d landing = player.getPos();
                double surfaceY = findSurfaceY(landing.x, landing.z, landing.y);
                if (!Double.isNaN(surfaceY)) {
                    Wave wave = createWave(new Vec3d(landing.x, surfaceY + 0.018, landing.z), now);
                    if (waves.size() >= MAX_WAVES) waves.remove(0);
                    waves.add(wave);
                    if (sound.getValue()) playImpact(wave.origin);
                }
            }
            state.reset(player.getY());
        } else if (onGround) {
            state.reset(player.getY());
        }
        state.wasOnGround = onGround;
    }

    private Wave createWave(Vec3d origin, long now) {
        float maxRadius = radius.getValue();
        Map<Long, SurfaceCell> surfaces = collectConnectedSurfaces(origin, maxRadius);
        return new Wave(origin, now, Math.max(1L, duration.getValue().longValue()), maxRadius, surfaces);
    }

    private Map<Long, SurfaceCell> collectConnectedSurfaces(Vec3d origin, float maxRadius) {
        Map<Long, SurfaceCell> surfaces = new HashMap<>();
        Map<Long, Float> bestDistances = new HashMap<>();
        PriorityQueue<SurfaceCell> pending = new PriorityQueue<>(
                Comparator.comparingDouble((SurfaceCell cell) -> cell.pathDistance));
        int startX = MathHelper.floor(origin.x);
        int startZ = MathHelper.floor(origin.z);
        SurfaceCell start = surfaceCell(startX, startZ, (float) (origin.y - SURFACE_LIFT), origin, 0.0f);
        pending.add(start);
        bestDistances.put(columnKey(startX, startZ), 0.0f);

        while (!pending.isEmpty()) {
            SurfaceCell current = pending.poll();
            long currentKey = columnKey(current.blockX, current.blockZ);
            float bestKnown = bestDistances.getOrDefault(currentKey, Float.MAX_VALUE);
            if (current.pathDistance > bestKnown + 0.001f || surfaces.containsKey(currentKey)) continue;
            surfaces.put(currentKey, current);

            for (int[] offset : SURFACE_NEIGHBORS) {
                int x = current.blockX + offset[0];
                int z = current.blockZ + offset[1];
                long key = columnKey(x, z);
                if (surfaces.containsKey(key)) continue;

                double centerX = x + 0.5;
                double centerZ = z + 0.5;
                double dx = centerX - origin.x;
                double dz = centerZ - origin.z;
                if (dx * dx + dz * dz > (maxRadius + 1.0f) * (maxRadius + 1.0f)) continue;

                double surfaceY = findConnectedSurfaceY(centerX, centerZ, current.surfaceY);
                if (Double.isNaN(surfaceY)) continue;
                float horizontalCost = offset[0] != 0 && offset[1] != 0 ? 1.41421356f : 1.0f;
                float nextDistance = current.pathDistance + horizontalCost
                        + Math.abs((float) surfaceY - current.surfaceY) * VERTICAL_PATH_COST;
                if (nextDistance > maxRadius + 1.35f
                        || nextDistance >= bestDistances.getOrDefault(key, Float.MAX_VALUE)) continue;

                bestDistances.put(key, nextDistance);
                pending.add(surfaceCell(x, z, (float) surfaceY, origin, nextDistance));
            }
        }
        return surfaces;
    }

    private static SurfaceCell surfaceCell(int blockX, int blockZ, float surfaceY, Vec3d origin,
                                           float pathDistance) {
        double dx = blockX + 0.5 - origin.x;
        double dz = blockZ + 0.5 - origin.z;
        return new SurfaceCell(blockX, blockZ, surfaceY, pathDistance,
                (float) Math.atan2(dz, dx));
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void playImpact(Vec3d origin) {
        double distance = mc.player.getPos().distanceTo(origin);
        float attenuation = MathHelper.clamp(1.0f - (float) distance / 32.0f, 0.0f, 1.0f);
        if (attenuation <= 0.0f) return;
        mc.world.playSound(origin.x, origin.y, origin.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS,
                0.42f * attenuation, 0.58f, false);
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck() || waves.isEmpty()) return;

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
        for (Wave wave : waves) {
            float t = MathHelper.clamp((now - wave.createdAt) / (float) wave.lifeMs, 0.0f, 1.0f);
            float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            float currentRadius = wave.maxRadius * eased;
            float fade = (float) Math.pow(1.0f - t, 1.35);
            float glow = intensity.getValue() * fade;

            renderWaveMode(builder, matrix, wave, camera, currentRadius, glow, t);
        }

        BuiltBuffer built = builder.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderWaveMode(BufferBuilder builder, Matrix4f matrix, Wave wave, Vec3d camera,
                                float currentRadius, float glow, float progress) {
        float phase = progress * MathHelper.TAU;
        switch (mode.getValue()) {
            case NEON -> {
                addBand(builder, matrix, wave, camera, currentRadius, 0.34f,
                        0.02f, 0.72f, 1.0f, 0.10f * glow,
                        WaveShape.CIRCLE, SurfaceStyle.FULL, phase, false);
                addBand(builder, matrix, wave, camera, currentRadius, 0.14f,
                        0.05f, 0.88f, 1.0f, 0.26f * glow,
                        WaveShape.CIRCLE, SurfaceStyle.FULL, phase, false);
                addBand(builder, matrix, wave, camera, currentRadius, 0.035f,
                        1.0f, 1.0f, 1.0f, 0.92f * glow,
                        WaveShape.CIRCLE, SurfaceStyle.FULL, phase, false);
            }
            case LIGHTNING -> addTerrainField(builder, matrix, wave, camera, currentRadius,
                    glow, phase, FieldMode.LIGHTNING, 0.16f, 0.72f, 1.0f);
            case NOVA -> addTerrainField(builder, matrix, wave, camera, currentRadius,
                    glow, phase, FieldMode.NOVA, 1.0f, 0.34f, 0.08f);
            case VORTEX -> addTerrainField(builder, matrix, wave, camera, currentRadius,
                    glow, phase, FieldMode.VORTEX, 0.76f, 0.12f, 1.0f);
        }
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        if (fullNullCheck() || waves.isEmpty() || !dimming.getValue()) return;

        long now = System.currentTimeMillis();
        float darkness = 0.0f;
        for (Wave wave : waves) {
            float t = MathHelper.clamp((now - wave.createdAt) / (float) wave.lifeMs, 0.0f, 1.0f);
            float distanceFade = MathHelper.clamp(1.0f - (float) mc.player.getPos().distanceTo(wave.origin) / 24.0f,
                    0.0f, 1.0f);
            darkness = Math.max(darkness, (1.0f - t) * (1.0f - t) * distanceFade);
        }

        float dimStrength = mode.getValue() == WaveMode.VORTEX ? 72.0f : 48.0f;
        int alpha = MathHelper.clamp((int) (darkness * intensity.getValue() * dimStrength), 0,
                mode.getValue() == WaveMode.VORTEX ? 92 : 64);
        if (alpha > 0) {
            event.getContext().fill(0, 0, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                    alpha << 24);
        }
    }

    private static void addBand(BufferBuilder builder, Matrix4f matrix, Wave wave, Vec3d camera,
                                float radius, float width, float red, float green, float blue, float alpha,
                                WaveShape shape, SurfaceStyle style, float phase, boolean broken) {
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        for (SurfaceCell cell : wave.surfaces) {
            float coverage = bandCoverage(cell.pathDistance, cell.angle, cell.blockX, cell.blockZ,
                    radius, width, shape, phase, broken);
            if (coverage > 0.005f) {
                putSurfaceTile(builder, matrix, cell, camera, red, green, blue, alpha * coverage, style);
            }

            renderVerticalBand(builder, matrix, wave, cell, 1, 0, camera,
                    radius, width, red, green, blue, alpha, shape, style, phase, broken);
            renderVerticalBand(builder, matrix, wave, cell, 0, 1, camera,
                    radius, width, red, green, blue, alpha, shape, style, phase, broken);
        }
    }

    private static void addVoidField(BufferBuilder builder, Matrix4f matrix, Wave wave, Vec3d camera,
                                     float radius, float glow, float progress) {
        for (SurfaceCell cell : wave.surfaces) {
            float behindFront = radius + 0.55f - cell.pathDistance;
            if (behindFront <= 0.0f) continue;

            float noise = cellNoise(cell.blockX, cell.blockZ);
            float dissolve = MathHelper.clamp(behindFront / 2.6f, 0.0f, 1.0f);
            if (noise + dissolve * 0.72f < 0.60f + progress * 0.12f) continue;

            float edgeFade = MathHelper.clamp(behindFront / 0.72f, 0.0f, 1.0f);
            float alpha = (0.055f + 0.16f * (1.0f - dissolve)) * edgeFade * glow;
            putSurfaceTile(builder, matrix, cell, camera,
                    0.35f, 0.015f, 0.56f, alpha, SurfaceStyle.VOID);
        }
    }

    private static void addTerrainField(BufferBuilder builder, Matrix4f matrix, Wave wave, Vec3d camera,
                                        float radius, float glow, float phase, FieldMode fieldMode,
                                        float red, float green, float blue) {
        float alpha = MathHelper.clamp(glow, 0.0f, 1.0f);
        for (SurfaceCell cell : wave.surfaces) {
            float coverage = fieldCoverage(cell.pathDistance, cell.angle, cell.blockX, cell.blockZ,
                    radius, phase, fieldMode);
            if (coverage > 0.012f) {
                float y = (float) (cell.surfaceY + SURFACE_LIFT - camera.y);
                float centerX = (float) (cell.blockX + 0.5 - camera.x);
                float centerZ = (float) (cell.blockZ + 0.5 - camera.z);
                float effectAlpha = alpha * coverage;
                switch (fieldMode) {
                    case LIGHTNING -> putLightningGlyph(builder, matrix, centerX, y, centerZ, cell.angle,
                            red, green, blue, effectAlpha, cellNoise(cell.blockX, cell.blockZ));
                    case NOVA -> putNovaGlyph(builder, matrix, centerX, y, centerZ, cell.angle,
                            red, green, blue, effectAlpha);
                    case VORTEX -> putVortexGlyph(builder, matrix, centerX, y, centerZ,
                            red, green, blue, effectAlpha);
                }
            }

            renderVerticalField(builder, matrix, wave, cell, 1, 0, camera,
                    radius, phase, fieldMode, red, green, blue, alpha);
            renderVerticalField(builder, matrix, wave, cell, 0, 1, camera,
                    radius, phase, fieldMode, red, green, blue, alpha);
        }
    }

    private static float fieldCoverage(float pathDistance, float angle, int blockX, int blockZ,
                                       float radius, float phase, FieldMode mode) {
        if (pathDistance > radius + 0.85f || pathDistance < 0.25f) return 0.0f;

        return switch (mode) {
            case LIGHTNING -> {
                int branches = 7;
                float sector = MathHelper.TAU / branches;
                int branch = Math.round((angle - phase * 0.045f) / sector);
                float baseAngle = branch * sector + phase * 0.045f;
                float jitter = (float) Math.sin(pathDistance * 3.7f + branch * 2.13f) * 0.18f
                        + (cellNoise(blockX, blockZ) - 0.5f) * 0.10f;
                float angleDistance = Math.abs(wrapAngle(angle - baseAngle - jitter));
                float angularWidth = 0.065f + 0.42f / (pathDistance + 1.0f);
                float branchShape = 1.0f - MathHelper.clamp(angleDistance / angularWidth, 0.0f, 1.0f);
                float head = 1.0f - MathHelper.clamp(Math.abs(pathDistance - radius) / 1.25f, 0.0f, 1.0f);
                float body = MathHelper.clamp((radius - pathDistance + 0.7f) / 2.8f, 0.0f, 1.0f) * 0.58f;
                yield branchShape * Math.max(head, body);
            }
            case NOVA -> {
                int rays = 12;
                float sector = MathHelper.TAU / rays;
                int ray = Math.round(angle / sector);
                float rayAngle = ray * sector + (float) Math.sin(pathDistance * 1.55f + ray) * 0.055f;
                float angleDistance = Math.abs(wrapAngle(angle - rayAngle));
                float angularWidth = 0.055f + 0.36f / (pathDistance + 1.0f);
                float rayShape = 1.0f - MathHelper.clamp(angleDistance / angularWidth, 0.0f, 1.0f);
                float head = 1.0f - MathHelper.clamp(Math.abs(pathDistance - radius) / 1.45f, 0.0f, 1.0f);
                float sparks = 0.62f + 0.38f * cellNoise(blockX + ray * 7, blockZ - ray * 5);
                yield rayShape * head * sparks;
            }
            case VORTEX -> {
                float spiral = phase * 0.58f + pathDistance * 0.72f;
                float firstArm = Math.abs(wrapAngle(angle - spiral));
                float secondArm = Math.abs(wrapAngle(angle - spiral - (float) Math.PI));
                float angleDistance = Math.min(firstArm, secondArm);
                float angularWidth = 0.13f + 0.30f / (pathDistance + 1.0f);
                float arm = 1.0f - MathHelper.clamp(angleDistance / angularWidth, 0.0f, 1.0f);
                float reveal = MathHelper.clamp((radius - pathDistance + 0.75f) / 1.35f, 0.0f, 1.0f);
                float pulse = 0.72f + 0.28f * (float) Math.sin(pathDistance * 2.4f - phase * 2.0f);
                yield arm * reveal * pulse;
            }
        };
    }

    private static float wrapAngle(float angle) {
        while (angle > Math.PI) angle -= MathHelper.TAU;
        while (angle < -Math.PI) angle += MathHelper.TAU;
        return angle;
    }

    private static void putLightningGlyph(BufferBuilder builder, Matrix4f matrix,
                                          float centerX, float y, float centerZ, float angle,
                                          float red, float green, float blue, float alpha, float noise) {
        float dx = (float) Math.cos(angle);
        float dz = (float) Math.sin(angle);
        float sx = -dz;
        float sz = dx;
        float bend = (noise - 0.5f) * 0.22f;

        float x0 = centerX - dx * 0.43f + sx * bend;
        float z0 = centerZ - dz * 0.43f + sz * bend;
        float x1 = centerX - dx * 0.10f - sx * 0.13f;
        float z1 = centerZ - dz * 0.10f - sz * 0.13f;
        float x2 = centerX + dx * 0.10f + sx * 0.10f;
        float z2 = centerZ + dz * 0.10f + sz * 0.10f;
        float x3 = centerX + dx * 0.43f - sx * bend * 0.55f;
        float z3 = centerZ + dz * 0.43f - sz * bend * 0.55f;

        putTopSegment(builder, matrix, x0, z0, x1, z1, y, 0.065f, red, green, blue, alpha * 0.72f);
        putTopSegment(builder, matrix, x1, z1, x2, z2, y, 0.085f, red, green, blue, alpha);
        putTopSegment(builder, matrix, x2, z2, x3, z3, y, 0.055f, 0.94f, 1.0f, 1.0f, alpha);
        if (noise > 0.74f) {
            putTopSegment(builder, matrix, x2, z2,
                    x2 + dx * 0.20f + sx * 0.18f, z2 + dz * 0.20f + sz * 0.18f,
                    y + 0.002f, 0.035f, red, green, blue, alpha * 0.72f);
        }
    }

    private static void putNovaGlyph(BufferBuilder builder, Matrix4f matrix,
                                     float centerX, float y, float centerZ, float angle,
                                     float red, float green, float blue, float alpha) {
        float dx = (float) Math.cos(angle);
        float dz = (float) Math.sin(angle);
        float sx = -dz;
        float sz = dx;
        float tipX = centerX + dx * 0.46f;
        float tipZ = centerZ + dz * 0.46f;
        float baseX = centerX - dx * 0.31f;
        float baseZ = centerZ - dz * 0.31f;

        builder.vertex(matrix, tipX, y, tipZ).color(1.0f, 0.94f, 0.82f, alpha);
        builder.vertex(matrix, baseX + sx * 0.19f, y, baseZ + sz * 0.19f).color(red, green, blue, alpha * 0.12f);
        builder.vertex(matrix, baseX - sx * 0.19f, y, baseZ - sz * 0.19f).color(red, green, blue, alpha * 0.12f);
        putTopSegment(builder, matrix, baseX, baseZ, tipX, tipZ, y + 0.002f,
                0.045f, 1.0f, 0.90f, 0.62f, alpha);
    }

    private static void putVortexGlyph(BufferBuilder builder, Matrix4f matrix,
                                       float centerX, float y, float centerZ,
                                       float red, float green, float blue, float alpha) {
        putTopPolygon(builder, matrix, centerX, y, centerZ, 0.25f, 10,
                red, green, blue, alpha * 0.38f);
        putTopPolygon(builder, matrix, centerX, y + 0.003f, centerZ, 0.105f, 8,
                0.96f, 0.88f, 1.0f, alpha);
    }

    private static void putTopSegment(BufferBuilder builder, Matrix4f matrix,
                                      float x0, float z0, float x1, float z1, float y, float width,
                                      float red, float green, float blue, float alpha) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001f) return;
        float px = -dz / length * width;
        float pz = dx / length * width;
        builder.vertex(matrix, x0 + px, y, z0 + pz).color(red, green, blue, alpha);
        builder.vertex(matrix, x1 + px, y, z1 + pz).color(red, green, blue, alpha);
        builder.vertex(matrix, x1 - px, y, z1 - pz).color(red, green, blue, alpha);
        builder.vertex(matrix, x0 + px, y, z0 + pz).color(red, green, blue, alpha);
        builder.vertex(matrix, x1 - px, y, z1 - pz).color(red, green, blue, alpha);
        builder.vertex(matrix, x0 - px, y, z0 - pz).color(red, green, blue, alpha);
    }

    private static void renderVerticalField(BufferBuilder builder, Matrix4f matrix, Wave wave,
                                            SurfaceCell cell, int offsetX, int offsetZ, Vec3d camera,
                                            float radius, float phase, FieldMode fieldMode,
                                            float red, float green, float blue, float alpha) {
        SurfaceCell neighbor = wave.surfaceAt(cell.blockX + offsetX, cell.blockZ + offsetZ);
        if (neighbor == null) return;
        float heightDifference = Math.abs(cell.surfaceY - neighbor.surfaceY);
        if (heightDifference < 0.045f) return;

        float lowY = Math.min(cell.surfaceY, neighbor.surfaceY);
        float highY = Math.max(cell.surfaceY, neighbor.surfaceY);
        SurfaceCell lowCell = cell.surfaceY <= neighbor.surfaceY ? cell : neighbor;
        SurfaceCell highCell = cell.surfaceY <= neighbor.surfaceY ? neighbor : cell;
        int verticalTiles = Math.max(1, MathHelper.ceil(heightDifference));

        for (int tile = 0; tile < verticalTiles; tile++) {
            float from = tile / (float) verticalTiles;
            float to = (tile + 1) / (float) verticalTiles;
            float midpoint = (from + to) * 0.5f;
            float pathDistance = MathHelper.lerp(midpoint, lowCell.pathDistance, highCell.pathDistance);
            float angle = lerpAngle(lowCell.angle, highCell.angle, midpoint);
            float coverage = fieldCoverage(pathDistance, angle, cell.blockX, cell.blockZ,
                    radius, phase, fieldMode);
            if (coverage <= 0.012f) continue;

            float y0 = (float) (MathHelper.lerp(from, lowY, highY) + 0.11f - camera.y);
            float y1 = (float) (MathHelper.lerp(to, lowY, highY) - 0.11f - camera.y);
            if (y1 <= y0) continue;
            float inset = switch (fieldMode) {
                case LIGHTNING -> 0.31f;
                case NOVA -> 0.20f;
                case VORTEX -> 0.27f;
            };
            float effectAlpha = MathHelper.clamp(alpha * coverage, 0.0f, 1.0f);

            if (offsetX != 0) {
                float outward = cell.surfaceY > neighbor.surfaceY ? SURFACE_LIFT : -SURFACE_LIFT;
                float faceX = (float) (cell.blockX + 1.0 + outward - camera.x);
                float z0 = (float) (cell.blockZ + inset - camera.z);
                float z1 = (float) (cell.blockZ + 1.0f - inset - camera.z);
                putVerticalQuadX(builder, matrix, faceX, y0, y1, z0, z1,
                        red, green, blue, effectAlpha);
            } else {
                float outward = cell.surfaceY > neighbor.surfaceY ? SURFACE_LIFT : -SURFACE_LIFT;
                float faceZ = (float) (cell.blockZ + 1.0 + outward - camera.z);
                float x0 = (float) (cell.blockX + inset - camera.x);
                float x1 = (float) (cell.blockX + 1.0f - inset - camera.x);
                putVerticalQuadZ(builder, matrix, faceZ, y0, y1, x0, x1,
                        red, green, blue, effectAlpha);
            }
        }
    }

    private static float bandCoverage(float pathDistance, float angle, int blockX, int blockZ,
                                      float radius, float width, WaveShape shape,
                                      float phase, boolean broken) {
        if (broken) {
            float gap = (float) Math.sin(blockX * 2.17 + blockZ * 3.41 + phase * 4.6);
            if (gap < -0.08f || cellNoise(blockX, blockZ) < 0.16f) return 0.0f;
        }

        float targetRadius = Math.max(0.0f, shapedRadius(radius, angle, shape, phase));
        float reach = width + 0.54f;
        float distance = Math.abs(pathDistance - targetRadius);
        if (distance >= reach) return 0.0f;

        float coverage = 1.0f - MathHelper.clamp(distance / reach, 0.0f, 1.0f);
        coverage = coverage * coverage * (3.0f - 2.0f * coverage);
        if (shape == WaveShape.ELECTRIC) {
            float flicker = 0.58f + 0.42f * Math.abs((float) Math.sin(
                    blockX * 4.13 + blockZ * 2.71 - phase * 8.0));
            coverage *= flicker;
        }
        return coverage;
    }

    private static float shapedRadius(float radius, double angle, WaveShape shape, float phase) {
        return switch (shape) {
            case CIRCLE -> radius;
            case ELECTRIC -> radius + 0.42f * (float) Math.sin(angle * 11.0 + phase * 5.0)
                    + 0.18f * (float) Math.sin(angle * 27.0 - phase * 8.0);
            case HEX -> {
                double sector = angle % (Math.PI / 3.0);
                if (sector < 0.0) sector += Math.PI / 3.0;
                double localAngle = sector - Math.PI / 6.0;
                yield radius * (float) (Math.cos(Math.PI / 6.0) / Math.cos(localAngle));
            }
        };
    }

    private static void putSurfaceTile(BufferBuilder builder, Matrix4f matrix, SurfaceCell cell, Vec3d camera,
                                       float red, float green, float blue, float alpha, SurfaceStyle style) {
        float inset = style == SurfaceStyle.VOID
                ? 0.07f + cellNoise(cell.blockX, cell.blockZ) * 0.13f : 0.025f;
        float minX = (float) (cell.blockX + inset - camera.x);
        float maxX = (float) (cell.blockX + 1.0f - inset - camera.x);
        float minZ = (float) (cell.blockZ + inset - camera.z);
        float maxZ = (float) (cell.blockZ + 1.0f - inset - camera.z);
        float y = (float) (cell.surfaceY + SURFACE_LIFT - camera.y);

        if (style == SurfaceStyle.BOLT) {
            float centerX = (minX + maxX) * 0.5f;
            float centerZ = (minZ + maxZ) * 0.5f;
            builder.vertex(matrix, centerX, y, minZ).color(red, green, blue, alpha);
            builder.vertex(matrix, maxX, y, centerZ).color(red, green, blue, alpha);
            builder.vertex(matrix, centerX, y, maxZ).color(red, green, blue, alpha);
            builder.vertex(matrix, centerX, y, minZ).color(red, green, blue, alpha);
            builder.vertex(matrix, centerX, y, maxZ).color(red, green, blue, alpha);
            builder.vertex(matrix, minX, y, centerZ).color(red, green, blue, alpha);
            return;
        }
        if (style == SurfaceStyle.HEX) {
            putTopPolygon(builder, matrix, (minX + maxX) * 0.5f, y, (minZ + maxZ) * 0.5f,
                    (maxX - minX) * 0.50f, 6, red, green, blue, alpha);
            return;
        }

        builder.vertex(matrix, minX, y, minZ).color(red, green, blue, alpha);
        builder.vertex(matrix, maxX, y, minZ).color(red, green, blue, alpha);
        builder.vertex(matrix, maxX, y, maxZ).color(red, green, blue, alpha);
        builder.vertex(matrix, minX, y, minZ).color(red, green, blue, alpha);
        builder.vertex(matrix, maxX, y, maxZ).color(red, green, blue, alpha);
        builder.vertex(matrix, minX, y, maxZ).color(red, green, blue, alpha);
    }

    private static void putTopPolygon(BufferBuilder builder, Matrix4f matrix, float centerX, float y,
                                      float centerZ, float radius, int sides,
                                      float red, float green, float blue, float alpha) {
        for (int side = 0; side < sides; side++) {
            double a0 = MathHelper.TAU * side / sides;
            double a1 = MathHelper.TAU * (side + 1) / sides;
            builder.vertex(matrix, centerX, y, centerZ).color(red, green, blue, alpha);
            builder.vertex(matrix, centerX + (float) Math.cos(a0) * radius, y,
                    centerZ + (float) Math.sin(a0) * radius).color(red, green, blue, alpha);
            builder.vertex(matrix, centerX + (float) Math.cos(a1) * radius, y,
                    centerZ + (float) Math.sin(a1) * radius).color(red, green, blue, alpha);
        }
    }

    private static void renderVerticalBand(BufferBuilder builder, Matrix4f matrix, Wave wave,
                                           SurfaceCell cell, int offsetX, int offsetZ, Vec3d camera,
                                           float radius, float width, float red, float green, float blue, float alpha,
                                           WaveShape shape, SurfaceStyle style, float phase, boolean broken) {
        SurfaceCell neighbor = wave.surfaceAt(cell.blockX + offsetX, cell.blockZ + offsetZ);
        if (neighbor == null) return;

        float heightDifference = Math.abs(cell.surfaceY - neighbor.surfaceY);
        if (heightDifference < 0.045f) return;

        float lowY = Math.min(cell.surfaceY, neighbor.surfaceY);
        float highY = Math.max(cell.surfaceY, neighbor.surfaceY);
        SurfaceCell lowCell = cell.surfaceY <= neighbor.surfaceY ? cell : neighbor;
        SurfaceCell highCell = cell.surfaceY <= neighbor.surfaceY ? neighbor : cell;
        int verticalTiles = Math.max(1, MathHelper.ceil(heightDifference));
        float inset = switch (style) {
            case BOLT -> 0.24f;
            case HEX -> 0.15f;
            case VOID -> 0.09f;
            default -> 0.025f;
        };

        for (int tile = 0; tile < verticalTiles; tile++) {
            float from = tile / (float) verticalTiles;
            float to = (tile + 1) / (float) verticalTiles;
            float midpoint = (from + to) * 0.5f;
            float pathDistance = MathHelper.lerp(midpoint, lowCell.pathDistance, highCell.pathDistance);
            float angle = lerpAngle(lowCell.angle, highCell.angle, midpoint);
            float coverage = bandCoverage(pathDistance, angle, cell.blockX, cell.blockZ,
                    radius, width, shape, phase, broken);
            if (coverage <= 0.005f) continue;

            float y0 = (float) (MathHelper.lerp(from, lowY, highY) + 0.006f - camera.y);
            float y1 = (float) (MathHelper.lerp(to, lowY, highY) - 0.006f - camera.y);
            if (y1 <= y0) continue;

            if (offsetX != 0) {
                float outward = cell.surfaceY > neighbor.surfaceY ? SURFACE_LIFT : -SURFACE_LIFT;
                float x = (float) (cell.blockX + 1.0 + outward - camera.x);
                float z0 = (float) (cell.blockZ + inset - camera.z);
                float z1 = (float) (cell.blockZ + 1.0f - inset - camera.z);
                putVerticalQuadX(builder, matrix, x, y0, y1, z0, z1,
                        red, green, blue, alpha * coverage);
            } else {
                float outward = cell.surfaceY > neighbor.surfaceY ? SURFACE_LIFT : -SURFACE_LIFT;
                float z = (float) (cell.blockZ + 1.0 + outward - camera.z);
                float x0 = (float) (cell.blockX + inset - camera.x);
                float x1 = (float) (cell.blockX + 1.0f - inset - camera.x);
                putVerticalQuadZ(builder, matrix, z, y0, y1, x0, x1,
                        red, green, blue, alpha * coverage);
            }
        }
    }

    private static void putVerticalQuadX(BufferBuilder builder, Matrix4f matrix, float x, float y0, float y1,
                                         float z0, float z1,
                                         float red, float green, float blue, float alpha) {
        builder.vertex(matrix, x, y0, z0).color(red, green, blue, alpha);
        builder.vertex(matrix, x, y1, z0).color(red, green, blue, alpha);
        builder.vertex(matrix, x, y1, z1).color(red, green, blue, alpha);
        builder.vertex(matrix, x, y0, z0).color(red, green, blue, alpha);
        builder.vertex(matrix, x, y1, z1).color(red, green, blue, alpha);
        builder.vertex(matrix, x, y0, z1).color(red, green, blue, alpha);
    }

    private static void putVerticalQuadZ(BufferBuilder builder, Matrix4f matrix, float z, float y0, float y1,
                                         float x0, float x1,
                                         float red, float green, float blue, float alpha) {
        builder.vertex(matrix, x0, y0, z).color(red, green, blue, alpha);
        builder.vertex(matrix, x0, y1, z).color(red, green, blue, alpha);
        builder.vertex(matrix, x1, y1, z).color(red, green, blue, alpha);
        builder.vertex(matrix, x0, y0, z).color(red, green, blue, alpha);
        builder.vertex(matrix, x1, y1, z).color(red, green, blue, alpha);
        builder.vertex(matrix, x1, y0, z).color(red, green, blue, alpha);
    }

    private static float lerpAngle(float from, float to, float delta) {
        float x = MathHelper.lerp(delta, (float) Math.cos(from), (float) Math.cos(to));
        float z = MathHelper.lerp(delta, (float) Math.sin(from), (float) Math.sin(to));
        return (float) Math.atan2(z, x);
    }

    private static float cellNoise(int x, int z) {
        int hash = x * 374761393 + z * 668265263;
        hash = (hash ^ (hash >>> 13)) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 0xffff) / 65535.0f;
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

    private double findConnectedSurfaceY(double x, double z, double previousY) {
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        int centerY = MathHelper.floor(previousY);
        int maxOffset = Math.max(MAX_SURFACE_RISE, MAX_SURFACE_DROP);

        // Search from the current height outwards. Ordinary terrain resolves after one
        // or two checks; a tall solid wall keeps scanning until its exposed top is found.
        for (int offset = 0; offset <= maxOffset; offset++) {
            double upper = offset <= MAX_SURFACE_RISE
                    ? exposedSurfaceAt(blockX, blockZ, centerY + offset, x, z) : Double.NaN;
            double lower = offset > 0 && offset <= MAX_SURFACE_DROP
                    ? exposedSurfaceAt(blockX, blockZ, centerY - offset, x, z) : Double.NaN;
            if (!Double.isNaN(upper) && !Double.isNaN(lower)) {
                return Math.abs(upper - previousY) <= Math.abs(lower - previousY) ? upper : lower;
            }
            if (!Double.isNaN(upper)) return upper;
            if (!Double.isNaN(lower)) return lower;
        }
        return Double.NaN;
    }

    private double exposedSurfaceAt(int blockX, int blockZ, int blockY, double x, double z) {
        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        BlockState state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(mc.world, pos);
        if (shape.isEmpty()) return Double.NaN;
        double topY = blockY + shape.getMax(Direction.Axis.Y);
        return isSurfaceExposed(x, z, topY) ? topY : Double.NaN;
    }

    private boolean isSurfaceExposed(double x, double z, double surfaceY) {
        double checkY = surfaceY + 0.002;
        BlockPos pos = BlockPos.ofFloored(x, checkY, z);
        VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
        if (shape.isEmpty()) return true;

        double localX = x - pos.getX();
        double localY = checkY - pos.getY();
        double localZ = z - pos.getZ();
        for (Box box : shape.getBoundingBoxes()) {
            if (localX > box.minX + 0.0001 && localX < box.maxX - 0.0001
                    && localY > box.minY + 0.0001 && localY < box.maxY - 0.0001
                    && localZ > box.minZ + 0.0001 && localZ < box.maxZ - 0.0001) {
                return false;
            }
        }
        return true;
    }

    private static final class LandingState {
        private boolean wasOnGround;
        private int airborneTicks;
        private double takeoffY;
        private double highestY;
        private float lowestVerticalSpeed;
        private float maxFallDistance;

        private LandingState(boolean wasOnGround, double y) {
            this.wasOnGround = wasOnGround;
            reset(y);
        }

        private void reset(double y) {
            airborneTicks = 0;
            takeoffY = y;
            highestY = y;
            lowestVerticalSpeed = 0.0f;
            maxFallDistance = 0.0f;
        }
    }

    public enum WaveMode implements Nameable {
        NEON("Neon"),
        LIGHTNING("Lightning"),
        NOVA("Nova"),
        VORTEX("Vortex");

        private final String name;

        WaveMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private enum WaveShape {
        CIRCLE,
        ELECTRIC,
        HEX
    }

    private enum FieldMode {
        LIGHTNING,
        NOVA,
        VORTEX
    }

    private enum SurfaceStyle {
        FULL,
        BOLT,
        HEX,
        VOID
    }

    private static final class SurfaceCell {
        private final int blockX;
        private final int blockZ;
        private final float surfaceY;
        private final float pathDistance;
        private final float angle;

        private SurfaceCell(int blockX, int blockZ, float surfaceY, float pathDistance,
                            float angle) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.surfaceY = surfaceY;
            this.pathDistance = pathDistance;
            this.angle = angle;
        }
    }

    private static final class Wave {
        private final Vec3d origin;
        private final long createdAt;
        private final long lifeMs;
        private final float maxRadius;
        private final List<SurfaceCell> surfaces;
        private final Map<Long, SurfaceCell> surfacesByColumn;

        private Wave(Vec3d origin, long createdAt, long lifeMs, float maxRadius,
                     Map<Long, SurfaceCell> surfacesByColumn) {
            this.origin = origin;
            this.createdAt = createdAt;
            this.lifeMs = lifeMs;
            this.maxRadius = maxRadius;
            this.surfacesByColumn = surfacesByColumn;
            this.surfaces = new ArrayList<>(surfacesByColumn.values());
        }

        private SurfaceCell surfaceAt(int blockX, int blockZ) {
            return surfacesByColumn.get(columnKey(blockX, blockZ));
        }
    }
}
