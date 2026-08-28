package dev.prostovisuals.mixin;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.client.events.impl.EventGameShutdown;
import dev.prostovisuals.client.util.math.Counter;
import dev.prostovisuals.client.managers.HitDetectionManager;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.client.util.WindowIconUtil;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements Wrapper {
    
    private static int tickCounter = 0;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void prostovisuals$setWindowIcon(CallbackInfo ci) {
        WindowIconUtil.applyAmongUsIcon((MinecraftClient) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        EventTick event = new EventTick();
        prostovisuals.getInstance().getEventHandler().post(event);
        Counter.updateFPS();
        
        // Периодическая очистка HitDetectionManager (каждые 20 тиков = 1 секунда)
        tickCounter++;
        if (tickCounter >= 20) {
            HitDetectionManager.getInstance().cleanup();
            tickCounter = 0;
        }
    }

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    public void updateWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("ProstoVisuals 0.4(BETA)");
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        // Публикуем событие выключения игры для автосохранения конфигурации
        prostovisuals.getInstance().getEventHandler().post(new EventGameShutdown());
    }
}