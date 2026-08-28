package dev.prostovisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.mixin.accessors.IWorldRenderer;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Safe batched hitbox renderer for 1.21.4.
 *
 * Important: BufferBuilder instances are created only after visible geometry is
 * known to exist, and fill/outline are built and submitted sequentially. This
 * avoids leaving an empty/open Tessellator buffer behind when no entities are
 * visible and avoids overlapping begin() calls on the shared Tessellator.
 */
public final class CustomHitBox extends Module {
    public @NotNull BooleanSetting players = new BooleanSetting("setting.players", true);
    public @NotNull BooleanSetting mobs = new BooleanSetting("setting.mobs", true);
    public @NotNull BooleanSetting fill = new BooleanSetting("setting.fill", true);
    public @NotNull NumberSetting lineWidth = new NumberSetting("setting.lineWidth", 1.5f, 0.5f, 6.0f, 0.1f);
    public @NotNull NumberSetting fillAlpha = new NumberSetting("setting.fillAlpha", 90, 0, 255, 1, () -> fill.getValue());
    public @NotNull NumberSetting outlineAlpha = new NumberSetting("setting.outlineAlpha", 255, 0, 255, 1);
    private final VisualColorSettings visualColor = new VisualColorSettings();

    private final ThemeManager themeManager = ThemeManager.getInstance();
    private final List<Box> visibleBoxes = new ArrayList<>(128);

    public CustomHitBox() {
        super("CustomHitBox", Category.Render, I18n.translate("module.customhitbox.description"));
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;
        renderBatched(event.getMatrices(), event.getTickDelta());
    }

    private void renderBatched(MatrixStack matrices, float tickDelta) {
        visibleBoxes.clear();

        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        var frustum = ((IWorldRenderer) mc.worldRenderer).getFrustum();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || entity.isInvisible()) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity instanceof PlayerEntity) {
                if (!players.getValue()) continue;
            } else if (!mobs.getValue()) {
                continue;
            }
            if (living.hasStatusEffect(StatusEffects.INVISIBILITY)) continue;

            Vec3d interpolated = entity.getLerpedPos(tickDelta);
            Vec3d current = entity.getPos();
            Box source = entity.getBoundingBox();
            double offX = interpolated.x - current.x;
            double offY = interpolated.y - current.y;
            double offZ = interpolated.z - current.z;

            Box box = new Box(
                    source.minX + offX - 0.002, source.minY + offY - 0.002, source.minZ + offZ - 0.002,
                    source.maxX + offX + 0.002, source.maxY + offY + 0.002, source.maxZ + offZ + 0.002
            );

            if (frustum != null && !frustum.isVisible(box)) continue;
            visibleBoxes.add(box);
        }

        // Critical fix: never begin/end an empty BufferBuilder.
        if (visibleBoxes.isEmpty()) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Color theme = visualColor.resolve();
        float r = theme.getRed() / 255.0f;
        float g = theme.getGreen() / 255.0f;
        float b = theme.getBlue() / 255.0f;
        float fillA = clamp255(fillAlpha.getValue().intValue()) / 255.0f;
        float lineA = clamp255(outlineAlpha.getValue().intValue()) / 255.0f;

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        try {
            // Build and submit fill first. Do not keep two builders open on the
            // shared Tessellator at the same time.
            if (fill.getValue() && fillA > 0.0f) {
                BufferBuilder fillBuffer = Tessellator.getInstance()
                        .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                for (Box box : visibleBoxes) {
                    appendFill(fillBuffer, matrix, camera, box, r, g, b, fillA);
                }
                var builtFill = fillBuffer.endNullable();
                if (builtFill != null) {
                    BufferRenderer.drawWithGlobalProgram(builtFill);
                }
            }

            if (lineA > 0.0f) {
                BufferBuilder lineBuffer = Tessellator.getInstance()
                        .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                for (Box box : visibleBoxes) {
                    appendOutline(lineBuffer, matrix, camera, box, r, g, b, lineA);
                }

                float width = Math.max(0.5f, lineWidth.getValue().floatValue());
                GL11.glLineWidth(width);
                var builtLines = lineBuffer.endNullable();
                if (builtLines != null) {
                    BufferRenderer.drawWithGlobalProgram(builtLines);
                }
            }
        } finally {
            GL11.glLineWidth(1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            visibleBoxes.clear();
        }
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void appendFill(BufferBuilder buffer, Matrix4f matrix, Vec3d camera, Box box,
                                   float r, float g, float b, float a) {
        float x0 = (float) (box.minX - camera.x), y0 = (float) (box.minY - camera.y), z0 = (float) (box.minZ - camera.z);
        float x1 = (float) (box.maxX - camera.x), y1 = (float) (box.maxY - camera.y), z1 = (float) (box.maxZ - camera.z);
        quad(buffer,matrix,x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,r,g,b,a);
        quad(buffer,matrix,x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,r,g,b,a);
        quad(buffer,matrix,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,r,g,b,a);
        quad(buffer,matrix,x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,r,g,b,a);
        quad(buffer,matrix,x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,r,g,b,a);
        quad(buffer,matrix,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,r,g,b,a);
    }

    private static void appendOutline(BufferBuilder buffer, Matrix4f matrix, Vec3d camera, Box box,
                                      float r, float g, float b, float a) {
        float x0 = (float) (box.minX - camera.x), y0 = (float) (box.minY - camera.y), z0 = (float) (box.minZ - camera.z);
        float x1 = (float) (box.maxX - camera.x), y1 = (float) (box.maxY - camera.y), z1 = (float) (box.maxZ - camera.z);
        line(buffer,matrix,x0,y0,z0,x0,y0,z1,r,g,b,a); line(buffer,matrix,x0,y0,z1,x1,y0,z1,r,g,b,a);
        line(buffer,matrix,x1,y0,z1,x1,y0,z0,r,g,b,a); line(buffer,matrix,x1,y0,z0,x0,y0,z0,r,g,b,a);
        line(buffer,matrix,x0,y1,z0,x0,y1,z1,r,g,b,a); line(buffer,matrix,x0,y1,z1,x1,y1,z1,r,g,b,a);
        line(buffer,matrix,x1,y1,z1,x1,y1,z0,r,g,b,a); line(buffer,matrix,x1,y1,z0,x0,y1,z0,r,g,b,a);
        line(buffer,matrix,x0,y0,z0,x0,y1,z0,r,g,b,a); line(buffer,matrix,x0,y0,z1,x0,y1,z1,r,g,b,a);
        line(buffer,matrix,x1,y0,z0,x1,y1,z0,r,g,b,a); line(buffer,matrix,x1,y0,z1,x1,y1,z1,r,g,b,a);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz,
                             float r,float g,float b,float a) {
        buffer.vertex(matrix,ax,ay,az).color(r,g,b,a); buffer.vertex(matrix,bx,by,bz).color(r,g,b,a);
        buffer.vertex(matrix,cx,cy,cz).color(r,g,b,a); buffer.vertex(matrix,dx,dy,dz).color(r,g,b,a);
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float r,float g,float b,float a) {
        buffer.vertex(matrix,ax,ay,az).color(r,g,b,a); buffer.vertex(matrix,bx,by,bz).color(r,g,b,a);
    }
}
