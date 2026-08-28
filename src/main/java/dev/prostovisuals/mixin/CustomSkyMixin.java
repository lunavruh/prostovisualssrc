package dev.prostovisuals.mixin;

import dev.prostovisuals.client.render.renderers.CustomSkyRenderer;
import dev.prostovisuals.modules.impl.render.CustomSky;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.RenderPass;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class CustomSkyMixin {
    @Shadow @Final private DefaultFramebufferSet framebufferSet;

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void prostovisuals$renderCustomSky(FrameGraphBuilder frameGraphBuilder, Camera camera,
                                               float tickDelta, Fog fog, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (prostovisuals.getInstance() == null || prostovisuals.getInstance().getModuleManager() == null) return;
        CustomSky module = prostovisuals.getInstance().getModuleManager().getModule(CustomSky.class);
        if (module == null || !module.isToggled() || shouldUseVanillaSky(camera)) return;
        // Let vanilla render while the framebuffer is settling after F11/window resize.
        if (!CustomSkyRenderer.framebufferReady()) return;

        RenderPass pass = frameGraphBuilder.createPass("prostovisuals_custom_sky");
        framebufferSet.mainFramebuffer = pass.transfer(framebufferSet.mainFramebuffer);
        pass.setRenderer(() -> CustomSkyRenderer.render(camera, tickDelta));
        ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void prostovisuals$removeVanillaClouds(FrameGraphBuilder frameGraphBuilder,
                                                   Matrix4f positionMatrix, Matrix4f projectionMatrix,
                                                   CloudRenderMode renderMode, Vec3d cameraPos,
                                                   float ticks, int color, float cloudHeight,
                                                   CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (prostovisuals.getInstance() == null || prostovisuals.getInstance().getModuleManager() == null) return;
        CustomSky module = prostovisuals.getInstance().getModuleManager().getModule(CustomSky.class);
        Camera camera = client.gameRenderer.getCamera();
        if (module != null && module.isToggled() && !shouldUseVanillaSky(camera)
                && CustomSkyRenderer.framebufferReady()) ci.cancel();
    }

    private static boolean shouldUseVanillaSky(Camera camera) {
        if (camera == null) return false;
        // Keep Minecraft's special visual treatment for water/lava/powder snow
        // and for screen-space darkness effects. Rendering our full-screen sky
        // over those states was the source of the white/overexposed blindness
        // frames seen in testing.
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return true;
        if (camera.getFocusedEntity() instanceof LivingEntity living) {
            return living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS);
        }
        return false;
    }
}
