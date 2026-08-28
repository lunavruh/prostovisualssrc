package dev.prostovisuals.mixin;

import dev.prostovisuals.client.events.impl.EventClickSlot;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

	@Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
	private void prostovisuals$clickSlotHook(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
		EventClickSlot event = new EventClickSlot(actionType, slotId, button, syncId);
		prostovisuals.getInstance().getEventHandler().post(event);
		if (event.isCancelled()) ci.cancel();
	}
} 
