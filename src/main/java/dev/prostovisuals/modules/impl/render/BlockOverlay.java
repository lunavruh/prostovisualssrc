package dev.prostovisuals.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.util.perf.Perf;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Fast block selection overlay.
 *
 * The old implementation allocated a List<Box> and submitted two independent
 * GPU draws for every cuboid in a VoxelShape.  Stairs/fences can contain many
 * cuboids, so merely looking at them created needless allocations and state
 * changes.  This implementation streams the shape directly into one fill
 * batch and one outline batch with no temporary Box/List objects.
 */
public final class BlockOverlay extends Module {
    private final NumberSetting lineWidth = new NumberSetting("setting.lineWidth", 2.0f, 1.0f, 5.0f, 0.1f);
    private final NumberSetting alpha = new NumberSetting("setting.alpha", 150, 0, 255, 1);
    private final BooleanSetting fill = new BooleanSetting("setting.fill", false, () -> true);
    private final NumberSetting fillAlpha = new NumberSetting("setting.fillAlpha", 50, 0, 255, 1, () -> fill.getValue());

    private final ThemeManager themeManager = ThemeManager.getInstance();

    public BlockOverlay() {
        super("BlockOverlay", Category.Render, I18n.translate("module.blockoverlay.description"));
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game event) {
        if (fullNullCheck()) return;

        try (var ignored = Perf.scopeCpu("BlockOverlay.onRender3D")) {
            HitResult hit = mc.crosshairTarget;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

            BlockPos pos = blockHit.getBlockPos();
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            double dx = mc.player.getX() - cx;
            double dy = mc.player.getY() - cy;
            double dz = mc.player.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz > 10_000.0) return;

            VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
            renderShape(event, pos, shape);
        }
    }

    private void renderShape(EventRender3D.Game event, BlockPos pos, VoxelShape shape) {
        Matrix4f matrix = event.getMatrices().peek().getPositionMatrix();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();

        Color top = themeManager.getCurrentTheme().getBackgroundColor();
        Color bottom = themeManager.getCurrentTheme().getSecondaryBackgroundColor();

        int outlineAlpha = clamp255(alpha.getValue().intValue());
        int insideAlpha = clamp255(fillAlpha.getValue().intValue());

        float topR = top.getRed() / 255.0f;
        float topG = top.getGreen() / 255.0f;
        float topB = top.getBlue() / 255.0f;
        float bottomR = bottom.getRed() / 255.0f;
        float bottomG = bottom.getGreen() / 255.0f;
        float bottomB = bottom.getBlue() / 255.0f;
        float lineA = outlineAlpha / 255.0f;
        float fillA = insideAlpha / 255.0f;

        BufferBuilder fillBuffer = fill.getValue()
                ? Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
                : null;
        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        final int bx = pos.getX();
        final int by = pos.getY();
        final int bz = pos.getZ();

        if (shape.isEmpty()) {
            appendBox(fillBuffer, lineBuffer, matrix, camera,
                    bx - 0.001, by - 0.001, bz - 0.001,
                    bx + 1.001, by + 1.001, bz + 1.001,
                    topR, topG, topB, bottomR, bottomG, bottomB, fillA, lineA);
        } else {
            shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                    appendBox(fillBuffer, lineBuffer, matrix, camera,
                            bx + minX - 0.001, by + minY - 0.001, bz + minZ - 0.001,
                            bx + maxX + 0.001, by + maxY + 0.001, bz + maxZ + 0.001,
                            topR, topG, topB, bottomR, bottomG, bottomB, fillA, lineA));
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        if (fillBuffer != null) {
            BufferRenderer.drawWithGlobalProgram(fillBuffer.end());
        }

        GL11.glLineWidth(lineWidth.getValue().floatValue());
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
        GL11.glLineWidth(1.0f);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void appendBox(BufferBuilder fillBuffer, BufferBuilder lineBuffer,
                                  Matrix4f matrix, Vec3d camera,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  float topR, float topG, float topB,
                                  float bottomR, float bottomG, float bottomB,
                                  float fillA, float lineA) {
        float x0 = (float) (minX - camera.x);
        float y0 = (float) (minY - camera.y);
        float z0 = (float) (minZ - camera.z);
        float x1 = (float) (maxX - camera.x);
        float y1 = (float) (maxY - camera.y);
        float z1 = (float) (maxZ - camera.z);

        if (fillBuffer != null) {
            // Top / bottom.
            quad(fillBuffer, matrix, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, topR,topG,topB, topR,topG,topB, fillA);
            quad(fillBuffer, matrix, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, bottomR,bottomG,bottomB, bottomR,bottomG,bottomB, fillA);
            // Four side faces use a vertical theme gradient.
            gradientQuad(fillBuffer, matrix, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, bottomR,bottomG,bottomB, topR,topG,topB, fillA);
            gradientQuad(fillBuffer, matrix, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, bottomR,bottomG,bottomB, topR,topG,topB, fillA);
            gradientQuad(fillBuffer, matrix, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, bottomR,bottomG,bottomB, topR,topG,topB, fillA);
            gradientQuad(fillBuffer, matrix, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, bottomR,bottomG,bottomB, topR,topG,topB, fillA);
        }

        // Bottom ring.
        line(lineBuffer,matrix,x0,y0,z0,x0,y0,z1,bottomR,bottomG,bottomB,bottomR,bottomG,bottomB,lineA);
        line(lineBuffer,matrix,x0,y0,z1,x1,y0,z1,bottomR,bottomG,bottomB,bottomR,bottomG,bottomB,lineA);
        line(lineBuffer,matrix,x1,y0,z1,x1,y0,z0,bottomR,bottomG,bottomB,bottomR,bottomG,bottomB,lineA);
        line(lineBuffer,matrix,x1,y0,z0,x0,y0,z0,bottomR,bottomG,bottomB,bottomR,bottomG,bottomB,lineA);
        // Top ring.
        line(lineBuffer,matrix,x0,y1,z0,x0,y1,z1,topR,topG,topB,topR,topG,topB,lineA);
        line(lineBuffer,matrix,x0,y1,z1,x1,y1,z1,topR,topG,topB,topR,topG,topB,lineA);
        line(lineBuffer,matrix,x1,y1,z1,x1,y1,z0,topR,topG,topB,topR,topG,topB,lineA);
        line(lineBuffer,matrix,x1,y1,z0,x0,y1,z0,topR,topG,topB,topR,topG,topB,lineA);
        // Vertical gradient edges.
        line(lineBuffer,matrix,x0,y0,z0,x0,y1,z0,bottomR,bottomG,bottomB,topR,topG,topB,lineA);
        line(lineBuffer,matrix,x0,y0,z1,x0,y1,z1,bottomR,bottomG,bottomB,topR,topG,topB,lineA);
        line(lineBuffer,matrix,x1,y0,z0,x1,y1,z0,bottomR,bottomG,bottomB,topR,topG,topB,lineA);
        line(lineBuffer,matrix,x1,y0,z1,x1,y1,z1,bottomR,bottomG,bottomB,topR,topG,topB,lineA);
    }

    private static void quad(BufferBuilder b, Matrix4f m,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float cx,float cy,float cz, float dx,float dy,float dz,
                             float r0,float g0,float bl0, float r1,float g1,float bl1, float a) {
        // Used for flat-colored top/bottom faces; both colors are kept in the
        // signature so call sites remain symmetrical with gradientQuad.
        b.vertex(m,ax,ay,az).color(r0,g0,bl0,a);
        b.vertex(m,bx,by,bz).color(r0,g0,bl0,a);
        b.vertex(m,cx,cy,cz).color(r1,g1,bl1,a);
        b.vertex(m,dx,dy,dz).color(r1,g1,bl1,a);
    }

    private static void gradientQuad(BufferBuilder b, Matrix4f m,
                                     float ax,float ay,float az, float bx,float by,float bz,
                                     float cx,float cy,float cz, float dx,float dy,float dz,
                                     float br,float bg,float bb, float tr,float tg,float tb, float a) {
        b.vertex(m,ax,ay,az).color(br,bg,bb,a);
        b.vertex(m,bx,by,bz).color(br,bg,bb,a);
        b.vertex(m,cx,cy,cz).color(tr,tg,tb,a);
        b.vertex(m,dx,dy,dz).color(tr,tg,tb,a);
    }

    private static void line(BufferBuilder b, Matrix4f m,
                             float ax,float ay,float az, float bx,float by,float bz,
                             float ar,float ag,float ab, float br,float bg,float bb, float a) {
        b.vertex(m,ax,ay,az).color(ar,ag,ab,a);
        b.vertex(m,bx,by,bz).color(br,bg,bb,a);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
