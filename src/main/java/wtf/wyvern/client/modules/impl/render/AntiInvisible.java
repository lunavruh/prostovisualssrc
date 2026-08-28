package wtf.wyvern.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import lombok.Generated;
import wtf.wyvern.base.events.impl.entity.EventEntityColor;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.ColorSetting;
import wtf.wyvern.utility.render.display.base.color.ColorRGBA;

@ModuleAnnotation(
   name = "AntiInvis",
   category = Category.RENDER,
   description = "Видно инвизок"
)
public final class AntiInvisible extends Module {
   public static final AntiInvisible INSTANCE = new AntiInvisible();
   private final ColorSetting colorSetting;

   private AntiInvisible() {
      this.colorSetting = new ColorSetting("Цвет", ColorRGBA.WHITE.mulAlpha(0.5F));
   }

   @EventTarget
   public void onEntityColor(EventEntityColor e) {
      e.setColor(this.colorSetting.getColor().getRGB());
      e.cancel();
   }

   @Generated
   public ColorSetting getColorSetting() {
      return this.colorSetting;
   }
}