package dev.prostovisuals.mixin;

import dev.prostovisuals.modules.impl.utility.NameProtect;
import dev.prostovisuals.client.managers.CosmeticsManager;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererNameProtectMixin<T extends Entity> {
    
    @ModifyVariable(
        method = "renderLabelIfPresent",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private Text modifyEntityLabel(Text text) {
        NameProtect nameProtect = NameProtect.getInstance();
        if (nameProtect != null && nameProtect.isToggled() && text != null) {
            String originalText = text.getString();
            String modifiedText = nameProtect.replaceNames(originalText);
            if (!originalText.equals(modifiedText)) {
                return Text.of(modifiedText);
            }
        }
        return text;
    }

    @Inject(
        method = "renderLabelIfPresent",
        at = @At("HEAD"),
        cancellable = true
    )
    private void prostovisuals$hideLocalModelName(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || state == null || text == null) return;
            if (prostovisuals.getInstance() == null) return;
            CosmeticsManager cosmetics = prostovisuals.getInstance().getCosmeticsManager();
            if (cosmetics == null || !cosmetics.hasSelection()) return;

            // EntityRenderState in Minecraft 1.21.4 does not expose an entity id.
            // renderLabelIfPresent receives the already resolved label, so identify the
            // local player's label by its unique profile name instead. This also works
            // with scoreboard prefixes/suffixes because the username remains in the label.
            String playerName = mc.player.getGameProfile().getName();
            String label = text.getString();
            if (playerName != null && !playerName.isEmpty() && label.contains(playerName)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
