package dev.prostovisuals.mixin;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.impl.render.CustomHitBox;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents a second vanilla hitbox pass when F3+B happens to be enabled.
 * CustomHitBox has its own batched renderer; doing geometry work here as well
 * only duplicated CPU/GPU work.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void prostovisuals$cancelVanillaHitbox(MatrixStack matrices,
                                                          VertexConsumer vertices,
                                                          Entity entity,
                                                          float r, float g, float b, float a,
                                                          CallbackInfo ci) {
        CustomHitBox module = prostovisuals.getInstance().getModuleManager().getModule(CustomHitBox.class);
        if (module != null && module.isToggled()) ci.cancel();
    }
}
