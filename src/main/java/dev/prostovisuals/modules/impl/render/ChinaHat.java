package dev.prostovisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.managers.FriendsManager;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
 

import static dev.prostovisuals.client.util.Wrapper.mc;

public class ChinaHat extends Module implements ThemeManager.ThemeChangeListener {

    public enum Style implements Nameable {
        PROSTOVISUAL("ProstoVisual"),
        WYVERN("Wyvern");
        private final String name;
        Style(String name) { this.name = name; }
        @Override public String getName() { return name; }
    }

    private final EnumSetting<Style> style = new EnumSetting<>("Hat Style", Style.PROSTOVISUAL);
    private final NumberSetting brimRadius = new NumberSetting("setting.brimRadius", 0.7f, 0.3f, 1.4f, 0.05f);
    private final BooleanSetting showFriends = new BooleanSetting("setting.showFriends", true);
    private final NumberSetting opacity = new NumberSetting("setting.opacity", 0.65f, 0.0f, 1.0f, 0.01f);
    private final VisualColorSettings visualColor = new VisualColorSettings();
    
    private static final int FIXED_SEGMENTS = 96;
    private final ThemeManager themeManager;
    private Color currentColor;

    public ChinaHat() {
        super("ChinaHat", Category.Render, net.minecraft.client.resource.language.I18n.translate("module.chinahat.description"));
        this.themeManager = ThemeManager.getInstance();
        this.currentColor = themeManager.getThemeColor();
        themeManager.addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) {
        this.currentColor = theme.getBackgroundColor();
    }

    @EventHandler
    public void onThemeChanged(dev.prostovisuals.client.events.impl.EventThemeChanged event) {
        this.currentColor = event.getTheme().getBackgroundColor();
    }

    @Override
    public void onEnable() {
        themeManager.addThemeChangeListener(this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        themeManager.removeThemeChangeListener(this);
        super.onDisable();
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game e) {
        if (fullNullCheck()) return;
        float tickDelta = e.getTickDelta();

        MatrixStack matrices = e.getMatrices();
        for (PlayerEntity p : mc.world.getPlayers()) {
            // Skip if it's the local player and we're in first person
            if (p == mc.player && mc.options.getPerspective().isFirstPerson()) continue;

            // For local player: always show (except in first person)
            if (p == mc.player) {
                renderHatForPlayer(matrices, p, tickDelta);
                continue;
            }

            // For other players: only show if they're friends and showFriends is enabled
            if (FriendsManager.checkFriend(p.getGameProfile().getName()) && showFriends.getValue()) {
                renderHatForPlayer(matrices, p, tickDelta);
            }
        }
    }

    private void renderHatForPlayer(MatrixStack matrices, PlayerEntity player, float tickDelta) {
        Vec3d pos = player.getLerpedPos(tickDelta);

        matrices.push();

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float eyeHeight = player.getEyeHeight(player.getPose());
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        float bodyYaw = MathHelper.lerp(tickDelta, player.prevBodyYaw, player.bodyYaw);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        float basePivotY = eyeHeight - 0.15f;
        matrices.translate(0.0, basePivotY, 0.0);
        float headYawAbs = MathHelper.lerp(tickDelta, player.prevHeadYaw, player.headYaw);
        float netHeadYaw = headYawAbs - bodyYaw;
        float headPitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        // Bind to head yaw and pitch so the hat follows head orientation
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(-netHeadYaw));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(headPitch));
        float baseToCrown = player.isInSneakingPose() ? 0.305f : 0.265f;
        float pitchRad = (float) Math.toRadians(MathHelper.clamp(headPitch, -90.0f, 90.0f));
        float cosPitch = MathHelper.clamp((float) Math.cos(pitchRad), 0.0f, 1.0f);
        float tiltFactor = 1.0f - cosPitch; // 0 at 0°, 1 at ±90°
        float upMul = headPitch < 0.0f ? 1.6f : 1.0f; // stronger adjustment when looking up

        float clearance = 0.03f * cosPitch + (headPitch < 0.0f ? 0.008f * tiltFactor : 0.0f);
        float dynamicOffset = -0.05f + 0.04f + 0.08f * tiltFactor * upMul;
        float forwardOffset = -0.06f * tiltFactor * Math.signum(headPitch) * upMul;
        matrices.translate(0.0, (baseToCrown + clearance) + dynamicOffset, forwardOffset);

        float configuredRadius = brimRadius.getValue().floatValue();
        if (style.getValue() == Style.WYVERN) {
            renderWyvernHat(matrices, Math.max(0.42f, configuredRadius * 0.92f), 0.30f, visualColor.resolve(), FIXED_SEGMENTS);
        } else {
            renderConeHatHollow(matrices, configuredRadius, 0.35f, visualColor.resolve(), FIXED_SEGMENTS);
        }

        matrices.pop();
    }

    private void renderConeHatHollow(MatrixStack matrices, float radius, float height, Color color, int segs) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDisable(GL11.GL_CULL_FACE);

        Matrix4f m = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float tipY = height;
        BufferBuilder coneStrip = tess.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        float tipAlpha = MathHelper.clamp(opacity.getValue().floatValue(), 0.0f, 1.0f);
        float baseAlpha = MathHelper.clamp(tipAlpha * 0.7f, 0.0f, 1.0f);
        for (int i = 0; i <= segs; i++) {
            double a = (i / (double) segs) * Math.PI * 2.0;
            float x = (float) (Math.cos(a) * radius);
            float z = (float) (Math.sin(a) * radius);
            coneStrip.vertex(m, x, 0f, z).color(r, g, b, baseAlpha);
            coneStrip.vertex(m, 0f, tipY, 0f).color(r, g, b, tipAlpha);
        }
        BufferRenderer.drawWithGlobalProgram(coneStrip.end());

        GL11.glEnable(GL11.GL_CULL_FACE);
    }
    /** Original Wyvern-inspired hat: denser cone with a clean animated-theme outline. */
    private void renderWyvernHat(MatrixStack matrices, float radius, float height, Color color, int segs) {
        renderConeHatHollow(matrices, radius, height, color, segs);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        GL11.glDisable(GL11.GL_CULL_FACE);
        RenderSystem.lineWidth(2.0f);
        Matrix4f m = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f;
        float a = MathHelper.clamp(opacity.getValue().floatValue() * 0.86f, 0f, 1f);
        BufferBuilder outline = tess.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double ang = (i / (double) segs) * Math.PI * 2.0;
            outline.vertex(m, (float)Math.cos(ang) * radius, 0f, (float)Math.sin(ang) * radius).color(r,g,b,a);
        }
        BufferRenderer.drawWithGlobalProgram(outline.end());
        GL11.glEnable(GL11.GL_CULL_FACE);
        RenderSystem.disableBlend();
    }

}
