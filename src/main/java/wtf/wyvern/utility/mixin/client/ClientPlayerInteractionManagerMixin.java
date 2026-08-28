package wtf.wyvern.utility.mixin.client;

import com.darkmagician6.eventapi.EventManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.wyvern.base.events.impl.player.EventAttack;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void wyvern$attackVisualEvent(PlayerEntity player, Entity target, CallbackInfo ci) {
        EventAttack event = new EventAttack(target);
        EventManager.call(event);
        if (event.isCancelled()) ci.cancel();
    }
}
