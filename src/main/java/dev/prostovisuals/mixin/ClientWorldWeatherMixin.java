package dev.prostovisuals.mixin;

import dev.prostovisuals.modules.impl.render.NoRender;
import dev.prostovisuals.prostovisuals;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class ClientWorldWeatherMixin {
    private static boolean prostovisuals$hideWeather() {
        try {
            var pv = prostovisuals.getInstance();
            if (pv == null || pv.getModuleManager() == null) return false;
            NoRender nr = pv.getModuleManager().getModule(NoRender.class);
            return nr != null && nr.isToggled() && nr.weather.getValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void prostovisuals$hideRainAndSnow(float delta, CallbackInfoReturnable<Float> cir) {
        if (prostovisuals$hideWeather()) cir.setReturnValue(0.0F);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void prostovisuals$hideThunder(float delta, CallbackInfoReturnable<Float> cir) {
        if (prostovisuals$hideWeather()) cir.setReturnValue(0.0F);
    }
}
