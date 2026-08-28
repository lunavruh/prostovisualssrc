package dev.prostovisuals.mixin;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.events.impl.EventMouse;
import dev.prostovisuals.client.spatial.SpatialDisplayManager;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    public void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        EventMouse event = new EventMouse(button, action);
        prostovisuals.getInstance().getEventHandler().post(event);

        // The Minecraft crosshair is the Spatial Display pointer. If it is over a monitor,
        // the click goes to that app and is not also used for attack/use inside Minecraft.
        if (SpatialDisplayManager.getInstance().handleMouseButton(button, action)) ci.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onSpatialMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (SpatialDisplayManager.getInstance().handleScroll(vertical)) ci.cancel();
    }
}
