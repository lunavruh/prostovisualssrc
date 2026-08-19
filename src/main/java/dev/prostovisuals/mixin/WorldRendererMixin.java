package dev.prostovisuals.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.client.util.world.WorldUtils;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void prostovisuals$renderHead(ObjectAllocator allocator, RenderTickCounter tickCounter,
                                         boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                         Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        EventRender3D.World event = new EventRender3D.World(camera, positionMatrix, tickCounter);
        prostovisuals.getInstance().getEventHandler().post(event);
        WorldUtils.lastWorld.set(positionMatrix);
        WorldUtils.lastProj.set(RenderSystem.getProjectionMatrix());
        WorldUtils.lastModelView.set(RenderSystem.getModelViewMatrix());
    }
}
