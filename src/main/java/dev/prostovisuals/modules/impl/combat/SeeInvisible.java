package dev.prostovisuals.modules.impl.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.impl.utility.NameProtect;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Reveals lightly-armoured invisible players with a theme-coloured halo.
 * The effect is intentionally geometry-based: it is cheaper than a separate
 * post-processing shader and remains compatible with the client's 1.21.4
 * POSITION_COLOR pipeline.
 */
public final class SeeInvisible extends Module {
    private static final int SEGMENTS = 64;
    private static final double MAX_DISTANCE_SQUARED = 256.0 * 256.0;
    private static final float HALO_RADIUS = 0.60f;
    private static final float[] RING_COS = new float[SEGMENTS + 1];
    private static final float[] RING_SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / SEGMENTS;
            RING_COS[i] = (float) Math.cos(angle);
            RING_SIN[i] = (float) Math.sin(angle);
        }
    }

    private final NumberSetting haloOpacity = new NumberSetting("setting.haloOpacity", 0.72f, 0.10f, 1.00f, 0.05f);
    private final NumberSetting haloSpeed = new NumberSetting("setting.haloSpeed", 1.0f, 0.20f, 3.00f, 0.10f);
    private final List<PlayerEntity> revealedPlayers = new ArrayList<>();

    public SeeInvisible() {
        super("SeeInvisible", Category.Combat, "module.seeinvisible.description");
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        float tickDelta = event.getTickDelta();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();
        Color themeColor = ThemeManager.getInstance().getCurrentTheme().getAccentColor();

        revealedPlayers.clear();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (shouldReveal(player) && mc.player.squaredDistanceTo(player) <= MAX_DISTANCE_SQUARED) {
                revealedPlayers.add(player);
            }
        }
        if (revealedPlayers.isEmpty()) return;

        float time = (System.currentTimeMillis() / 1000.0f) * haloSpeed.getValue();

        beginRender();
        try {
            BufferBuilder haloBuffer = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (PlayerEntity player : revealedPlayers) {
                appendHalo(haloBuffer, matrices, player, camera, tickDelta, time, themeColor);
            }
            BufferRenderer.drawWithGlobalProgram(haloBuffer.end());
        } finally {
            endRender();
        }

        // Draw the ordinary vanilla-style player label in a separate pass so
        // it does not inherit the raw halo shader state.
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        boolean drewAnyName = false;
        for (PlayerEntity player : revealedPlayers) {
            renderVanillaName(matrices, player, camera, tickDelta, consumers);
            drewAnyName = true;
        }
        if (drewAnyName) consumers.draw();
    }

    private boolean shouldReveal(PlayerEntity player) {
        if (player == mc.player || !player.isAlive() || player.isSpectator()) return false;
        if (!player.isInvisible()) return false;

        int equippedArmorPieces = 0;
        for (ItemStack armor : player.getArmorItems()) {
            if (!armor.isEmpty() && ++equippedArmorPieces > 2) return false;
        }
        return true;
    }

    private void beginRender() {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        // Never reveal the target through solid blocks.
        RenderSystem.enableDepthTest();
    }

    private void endRender() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void appendHalo(BufferBuilder buffer, MatrixStack matrices, PlayerEntity player, Vec3d camera,
                            float tickDelta, float time, Color color) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        float height = Math.max(1.0f, player.getHeight());
        float radius = HALO_RADIUS;
        float opacity = haloOpacity.getValue();

        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y + 0.02, pos.z - camera.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Only the wide bottom halo remains. There is deliberately no closed
        // cylindrical wall and no ring over the player's head.
        appendRingGlow(buffer, matrix, radius, 0.022f, color, opacity);
        appendRisingWaterfall(buffer, matrix, radius, height, time, player.getId(), color, opacity);

        matrices.pop();
    }

    private static void appendGradientStrip(BufferBuilder buffer, Matrix4f matrix,
                                            float radius, float bottomY, float topY,
                                            Color color, float bottomAlpha, float topAlpha) {
        float red = color.getRed() / 255.0f;
        float green = color.getGreen() / 255.0f;
        float blue = color.getBlue() / 255.0f;
        float bottomA = clamp01(bottomAlpha);
        float topA = clamp01(topAlpha);
        for (int i = 0; i < SEGMENTS; i++) {
            float x0 = RING_COS[i] * radius;
            float z0 = RING_SIN[i] * radius;
            float x1 = RING_COS[i + 1] * radius;
            float z1 = RING_SIN[i + 1] * radius;
            buffer.vertex(matrix, x0, bottomY, z0).color(red, green, blue, bottomA);
            buffer.vertex(matrix, x0, topY, z0).color(red, green, blue, topA);
            buffer.vertex(matrix, x1, topY, z1).color(red, green, blue, topA);
            buffer.vertex(matrix, x1, bottomY, z1).color(red, green, blue, bottomA);
        }
    }

    private static void appendBand(BufferBuilder buffer, Matrix4f matrix,
                                   float radius, float bottomY, float topY,
                                   Color color, float alpha) {
        appendGradientStrip(buffer, matrix, radius, bottomY, topY, color, alpha, alpha);
    }

    private static void appendRingGlow(BufferBuilder buffer, Matrix4f matrix, float radius, float y,
                                       Color color, float opacity) {
        // Bright core plus wide, soft outer glow at the feet.
        appendBand(buffer, matrix, radius * 0.985f, y - 0.010f, y + 0.010f, color, opacity * 0.90f);
        appendBand(buffer, matrix, radius * 1.025f, y - 0.018f, y + 0.018f, color, opacity * 0.48f);
        appendBand(buffer, matrix, radius * 1.080f, y - 0.028f, y + 0.028f, color, opacity * 0.22f);
        appendBand(buffer, matrix, radius * 1.150f, y - 0.038f, y + 0.038f, color, opacity * 0.08f);
    }

    private void renderVanillaName(MatrixStack matrices, PlayerEntity player, Vec3d camera,
                                   float tickDelta, VertexConsumerProvider consumers) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        String name = player.getGameProfile().getName();
        NameProtect nameProtect = NameProtect.getInstance();
        if (nameProtect != null) name = nameProtect.getProtectedName(name);
        if (name == null || name.isBlank()) return;

        matrices.push();
        matrices.translate(
                pos.x - camera.x,
                pos.y - camera.y + player.getHeight() + 0.50f,
                pos.z - camera.z
        );
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());
        matrices.scale(0.025f, -0.025f, 0.025f);

        float textX = -mc.textRenderer.getWidth(name) * 0.5f;
        mc.textRenderer.draw(
                name,
                textX, 0.0f,
                0xFFFFFFFF,
                false,
                matrices.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0x60000000,
                15728880
        );
        matrices.pop();
    }

    /**
     * Sparse glowing ribbons rise from the ground ring like a reversed
     * waterfall. Gaps between ribbons keep the effect open and prevent the
     * old glass-cylinder silhouette.
     */
    private static void appendRisingWaterfall(BufferBuilder buffer, Matrix4f matrix,
                                              float baseRadius, float height,
                                              float time, int entityId, Color color, float opacity) {
        final int streams = 12;
        for (int stream = 0; stream < streams; stream++) {
            float baseAngle = (float) (Math.PI * 2.0 * stream / streams)
                    + entityId * 0.071f;
            float phase = stream * 0.613f + entityId * 0.037f;

            // Broad translucent glow followed by a narrow bright core.
            appendRisingRibbon(buffer, matrix, baseRadius, height, time, baseAngle, phase,
                    0.090f, color, opacity * 0.24f);
            appendRisingRibbon(buffer, matrix, baseRadius, height, time, baseAngle, phase,
                    0.032f, color, opacity * 0.72f);
        }
    }

    private static void appendRisingRibbon(BufferBuilder buffer, Matrix4f matrix,
                                           float baseRadius, float height,
                                           float time, float baseAngle, float phase,
                                           float width, Color color, float alphaScale) {
        final int slices = 14;
        float red = color.getRed() / 255.0f;
        float green = color.getGreen() / 255.0f;
        float blue = color.getBlue() / 255.0f;
        float prevLeftX = 0f, prevLeftZ = 0f, prevRightX = 0f, prevRightZ = 0f;
        float prevY = 0f, prevAlpha = 0f;

        for (int slice = 0; slice <= slices; slice++) {
            float progress = slice / (float) slices;
            float y = 0.045f + progress * height * 0.93f;

            // The stream gently bends and narrows while rising.
            float angle = baseAngle
                    + (float) Math.sin(progress * 4.2f + time * 1.15f + phase) * 0.105f;
            float radius = baseRadius * (1.0f - progress * 0.28f)
                    + (float) Math.sin(progress * 7.0f - time * 1.7f + phase) * 0.018f;
            float centerX = (float) Math.cos(angle) * radius;
            float centerZ = (float) Math.sin(angle) * radius;
            float tangentX = -(float) Math.sin(angle);
            float tangentZ = (float) Math.cos(angle);
            float halfWidth = width * (1.0f - progress * 0.52f);

            // Repeating highlights visibly travel from the ring upward.
            float travelling = 0.5f + 0.5f * (float) Math.sin(
                    (progress * 2.35f - time * 0.72f + phase) * Math.PI * 2.0);
            travelling *= travelling;
            float baseFade = (float) Math.pow(1.0f - progress, 0.72f);
            float alpha = clamp01(alphaScale * baseFade * (0.22f + 0.78f * travelling));

            float leftX = centerX - tangentX * halfWidth;
            float leftZ = centerZ - tangentZ * halfWidth;
            float rightX = centerX + tangentX * halfWidth;
            float rightZ = centerZ + tangentZ * halfWidth;

            if (slice > 0) {
                buffer.vertex(matrix, prevLeftX, prevY, prevLeftZ).color(red, green, blue, prevAlpha);
                buffer.vertex(matrix, prevRightX, prevY, prevRightZ).color(red, green, blue, prevAlpha);
                buffer.vertex(matrix, rightX, y, rightZ).color(red, green, blue, alpha);
                buffer.vertex(matrix, leftX, y, leftZ).color(red, green, blue, alpha);
            }
            prevLeftX = leftX;
            prevLeftZ = leftZ;
            prevRightX = rightX;
            prevRightZ = rightZ;
            prevY = y;
            prevAlpha = alpha;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
