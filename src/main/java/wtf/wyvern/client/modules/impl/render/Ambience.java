package wtf.wyvern.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.events.impl.render.EventFog;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.BooleanSetting;
import wtf.wyvern.client.modules.api.setting.impl.NumberSetting;
import wtf.wyvern.utility.render.display.base.color.ColorRGBA;

import java.awt.Color;

@ModuleAnnotation(
        name = "Ambience",
        category = Category.RENDER,
        description = "Управляет атмосферой мира: временем и туманом"
)
public class Ambience extends Module {
    public static final Ambience INSTANCE = new Ambience();

    public final BooleanSetting customTime = new BooleanSetting("Свое время", true);
    public final NumberSetting timeSetting = new NumberSetting("Время", 18.0F, 0.0F, 24.0F, 0.1F, customTime::isEnabled);

    public final BooleanSetting customFog = new BooleanSetting("Свой туман", true);
    public final NumberSetting distanceSetting = new NumberSetting("Дальность", 120.0F, 0.0F, 500.0F, 5.0F, customFog::isEnabled);
    public final NumberSetting startMultiplier = new NumberSetting("Начало", 18.0F, 0.0F, 480.0F, 1.0F, customFog::isEnabled);
    public final NumberSetting colorIntensity = new NumberSetting("Насыщенность", 0.5F, 0.0F, 1.0F, 0.05F, customFog::isEnabled);

    private Ambience() {
    }

    @EventTarget
    public void onFog(EventFog e) {
        if (!customFog.isEnabled()) return;

        // Visual-only fog. Never touch Minecraft render/view distance or chunk loading.
        // A zero end distance means "do not override vanilla fog" instead of
        // producing a near-zero fog plane which looks like chunks are unloading.
        float endDistance = Math.max(0.0F, this.distanceSetting.getCurrent());
        if (endDistance <= 0.001F) return;

        float startDistance = Math.max(0.0F, this.startMultiplier.getCurrent());
        startDistance = Math.min(startDistance, Math.max(0.0F, endDistance - 0.5F));

        e.setDistance(endDistance);
        e.setStartDistance(startDistance);

        ColorRGBA themeColor = Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor();
        float[] hsb = Color.RGBtoHSB(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), null);

        float saturation = hsb[1] * this.colorIntensity.getCurrent();
        float brightness = Math.min(1.0F, hsb[2] + 0.15F);

        Color fogColor = Color.getHSBColor(hsb[0], saturation, brightness);
        e.setColor(fogColor.getRGB());
        e.setCancelled(true);
    }

    public boolean isTimeEnabled() {
        return isEnabled() && customTime.isEnabled();
    }
}