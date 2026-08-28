package dev.prostovisuals.mixin;

import dev.prostovisuals.modules.impl.utility.Cape;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.prostovisuals.prostovisuals;

@Mixin(value = PlayerListEntry.class, priority = 2000)
public class MixinCapeFeatureRenderer {

    // Cape renderers expect the vanilla 2:1 cape atlas. The square HUD icon was
    // sampled with cape UVs and produced the solid black rectangle seen in game.
    private static final Identifier CUSTOM_CAPE = Identifier.of("prostovisuals", "textures/amongus_cape.png");

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    public void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        if (prostovisuals.getInstance().getModuleManager().getModule(Cape.class).isToggled()) {
            SkinTextures original = cir.getReturnValue();
            cir.setReturnValue(new SkinTextures(
                    original.texture(),
                    original.textureUrl(),
                    CUSTOM_CAPE,
                    original.elytraTexture(),
                    original.model(),
                    original.secure()
            ));
        }
    }
}
