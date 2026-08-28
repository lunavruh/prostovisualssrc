package dev.prostovisuals.mixin;

import dev.prostovisuals.client.ui.mainmenu.MainMenu;
import dev.prostovisuals.client.util.Wrapper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla title screen with the integrated Wyvern-style client menu. */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen implements Wrapper {
    protected MixinTitleScreen(Text title) { super(title); }

    @Inject(method = "init", at = @At("RETURN"))
    private void prostovisuals$openMainMenu(CallbackInfo ci) {
        if (!(mc.currentScreen instanceof MainMenu)) mc.setScreen(new MainMenu());
    }
}
