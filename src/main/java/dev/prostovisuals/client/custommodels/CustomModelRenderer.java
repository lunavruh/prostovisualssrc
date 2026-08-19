package dev.prostovisuals.client.custommodels;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.impl.render.CustomModels;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Stateless bridge used by the player renderer mixin. */
public final class CustomModelRenderer {
    private static final Identifier TRALALERO_TEXTURE = Identifier.of("prostovisuals", "textures/custommodels/tralalero.png");
    private static final TralaleroModel TRALALERO = new TralaleroModel();

    private CustomModelRenderer() {}

    public static boolean shouldReplace(PlayerEntityRenderState state) {
        if (state == null || prostovisuals.getInstance() == null || prostovisuals.getInstance().getModuleManager() == null) return false;
        CustomModels module = prostovisuals.getInstance().getModuleManager().getModule(CustomModels.class);
        if (module == null || !module.isToggled() || !module.isTralaleroSelected()) return false;
        if (net.minecraft.client.MinecraftClient.getInstance().player == null) return false;
        return state.id == net.minecraft.client.MinecraftClient.getInstance().player.getId();
    }

    public static void render(PlayerEntityRenderState state, MatrixStack matrices, VertexConsumerProvider providers, int light) {
        TRALALERO.setAngles(state);

        matrices.push();
        // LivingEntityRenderer normally applies these body-space transforms before drawing its model.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - state.bodyYaw));
        matrices.scale(-1.0f, -1.0f, 1.0f);
        matrices.translate(0.0f, -1.49f, 0.0f);

        VertexConsumer vertices = providers.getBuffer(RenderLayer.getEntityCutoutNoCull(TRALALERO_TEXTURE));
        TRALALERO.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
