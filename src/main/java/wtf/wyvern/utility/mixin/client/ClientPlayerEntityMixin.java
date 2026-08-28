package wtf.wyvern.utility.mixin.client;

import com.darkmagician6.eventapi.EventManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.wyvern.base.events.impl.player.EventUpdate;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void wyvern$visualTick(CallbackInfo ci) {
        EventManager.call(new EventUpdate());
    }
}
