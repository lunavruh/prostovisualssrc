package dev.prostovisuals.mixin;

import dev.prostovisuals.modules.impl.render.CustomFog;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Fog;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom fog that keeps vanilla blindness/darkness behavior intact. */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private static void prostovisuals$getFogColor(
            Camera camera,
            float tickDelta,
            ClientWorld world,
            int viewDistance,
            float skyDarkness,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        CustomFog fog = getModule();
        if (fog == null || !fog.isToggled() || shouldUseVanillaFog(camera)) return;

        var color = fog.getSkyColor();
        cir.setReturnValue(new Vector4f(
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                1.0f
        ));
    }

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true)
    private static void prostovisuals$applyFog(
            Camera camera,
            BackgroundRenderer.FogType fogType,
            Vector4f color,
            float viewDistance,
            boolean thickenFog,
            float tickDelta,
            CallbackInfoReturnable<Fog> cir
    ) {
        CustomFog customFog = getModule();
        if (customFog == null || !customFog.isToggled() || shouldUseVanillaFog(camera)) return;

        Fog vanilla = cir.getReturnValue();
        if (vanilla == null) return;

        float end = Math.max(1.0f, Math.min(customFog.getFogDistance(), Math.max(1.0f, viewDistance)));
        // Sky fog starts at the camera so the horizon blends into exactly the
        // same color as terrain fog. Terrain keeps the user-controlled near
        // clear zone. This removes the bright hard horizon band.
        float start = fogType == BackgroundRenderer.FogType.FOG_SKY
                ? 0.0f
                : Math.max(0.0f, Math.min(customFog.getFogStartDistance(), end - 0.25f));
        var customColor = customFog.getSkyColor();

        // Use one identical color for the clear/fog result.  This removes the
        // visible horizon seam that appeared when terrain and sky fog diverged.
        cir.setReturnValue(new Fog(
                start,
                end,
                vanilla.shape(),
                customColor.getRed() / 255.0f,
                customColor.getGreen() / 255.0f,
                customColor.getBlue() / 255.0f,
                1.0f
        ));
    }

    private static boolean shouldUseVanillaFog(Camera camera) {
        if (camera == null) return false;
        // Water/lava/powder-snow have their own physically important fog.
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return true;
        if (camera.getFocusedEntity() instanceof LivingEntity living) {
            return living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS);
        }
        return false;
    }

    private static CustomFog getModule() {
        var client = prostovisuals.getInstance();
        if (client == null || client.getModuleManager() == null) return null;
        return client.getModuleManager().getModule(CustomFog.class);
    }
}
