package dev.prostovisuals.mixin;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.ui.hud.impl.HotbarHUD;
import dev.prostovisuals.client.ui.hud.PauseHudGate;
import dev.prostovisuals.modules.impl.render.Crosshair;
import dev.prostovisuals.modules.impl.render.NoRender;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Unique
    private HotbarHUD prostovisuals$hotbar;

    @Unique
    private HotbarHUD prostovisuals$getHotbar() {
        if (prostovisuals$hotbar != null) return prostovisuals$hotbar;
        for (var element : prostovisuals.getInstance().getHudManager().getHudElements()) {
            if (element instanceof HotbarHUD hotbar) {
                prostovisuals$hotbar = hotbar;
                return hotbar;
            }
        }
        return null;
    }

    @Unique
    private boolean prostovisuals$customHotbarEnabled() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) return false;
            var setting = prostovisuals.getInstance().getHudManager().getElements().getName("Hotbar");
            return setting instanceof BooleanSetting bs && bs.getValue() && prostovisuals$getHotbar() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private void prostovisuals$pushHotbarGroup(DrawContext context) {
        HotbarHUD hotbar = prostovisuals$getHotbar();
        if (hotbar != null) hotbar.pushVanillaGroupTransform(context);
    }

    @Unique
    private void prostovisuals$popHotbarGroup(DrawContext context) {
        HotbarHUD hotbar = prostovisuals$getHotbar();
        if (hotbar != null) hotbar.popVanillaGroupTransform(context);
    }


    @Inject(method = "render", at = @At("HEAD"))
    public void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // Do not draw custom liquid-glass HUD under pause/settings screens. Minecraft applies its
        // own dim/blur there; sampling that framebuffer made HUD capsules turn into opaque black bars.
        // ChatScreen stays supported because it is also the HUD editor. Spatial 3D displays are
        // rendered in the world pass and therefore remain visible behind Esc independently.
        MinecraftClient client = MinecraftClient.getInstance();
        if (PauseHudGate.shouldSuppress(client)) return;
        EventRender2D event = new EventRender2D(context, tickCounter);
        prostovisuals.getInstance().getEventHandler().post(event);
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    public void renderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) ci.cancel();
    }

    // Custom Hotbar replaces only the vanilla hotbar itself.
    // Vanilla health, hunger and experience UI stays enabled.


    @Inject(method = "renderStatusBars", at = @At("HEAD"))
    private void prostovisuals$statusBarsHead(DrawContext context, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$pushHotbarGroup(context);
    }

    @Inject(method = "renderStatusBars", at = @At("RETURN"))
    private void prostovisuals$statusBarsReturn(DrawContext context, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$popHotbarGroup(context);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void prostovisuals$xpHead(DrawContext context, int x, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$pushHotbarGroup(context);
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void prostovisuals$xpReturn(DrawContext context, int x, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$popHotbarGroup(context);
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"))
    private void prostovisuals$levelHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$pushHotbarGroup(context);
    }

    @Inject(method = "renderExperienceLevel", at = @At("RETURN"))
    private void prostovisuals$levelReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$popHotbarGroup(context);
    }

    @Inject(method = "renderMountHealth", at = @At("HEAD"))
    private void prostovisuals$mountHealthHead(DrawContext context, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$pushHotbarGroup(context);
    }

    @Inject(method = "renderMountHealth", at = @At("RETURN"))
    private void prostovisuals$mountHealthReturn(DrawContext context, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$popHotbarGroup(context);
    }

    @Inject(method = "renderMountJumpBar", at = @At("HEAD"))
    private void prostovisuals$mountJumpHead(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$pushHotbarGroup(context);
    }

    @Inject(method = "renderMountJumpBar", at = @At("RETURN"))
    private void prostovisuals$mountJumpReturn(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        if (prostovisuals$customHotbarEnabled()) prostovisuals$popHotbarGroup(context);
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    public void renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (prostovisuals.getInstance().getModuleManager().getModule(NoRender.class).isToggled() && prostovisuals.getInstance().getModuleManager().getModule(NoRender.class).potions.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    public void renderScoreboardSidebar(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
        boolean noRender = prostovisuals.getInstance().getModuleManager().getModule(NoRender.class).isToggled()
                && prostovisuals.getInstance().getModuleManager().getModule(NoRender.class).scoreboard.getValue();
        boolean customScoreboard = false;
        try {
            var setting = prostovisuals.getInstance().getHudManager().getElements().getName("Scoreboard");
            customScoreboard = setting instanceof BooleanSetting bs && bs.getValue();
        } catch (Throwable ignored) {}
        // When our custom scoreboard is enabled, only the vanilla background/layout
        // is suppressed. ScoreboardHUD reads the same live Scoreboard/Text objects,
        // so server text/formatting is preserved instead of reconstructed from strings.
        if (noRender || customScoreboard) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    public void renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (prostovisuals.getInstance().getModuleManager().getModule(Crosshair.class).isToggled()) {
            ci.cancel(); // Отменяем рендеринг стандартного прицела
        }
    }

    @Unique
    private boolean isPotion(ItemStack stack, StatusEffect status) {
        if (!(stack.getItem() instanceof PotionItem)) return false;
        PotionContentsComponent component = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (component == null) return false;
        if (component.potion().isEmpty()) return false;
        for (StatusEffectInstance effect : component.potion().get().value().getEffects()) {
            if (effect.getEffectType().value() == status) return true;
        }
        return false;
    }
}