package wtf.wyvern.utility.render.display.base;

import lombok.Generated;
import net.minecraft.util.Identifier;
import wtf.wyvern.Wyvern;

public class CustomSprite {
   private final Identifier texture;

   public CustomSprite(String path) {
      if (path.contains(":")) {
         this.texture = Identifier.of(path);
      } else if (path.contains("/")) {
         this.texture = Wyvern.id(path);
      } else {
         this.texture = Wyvern.id("icons/category/" + path);
      }

   }

   @Generated
   public Identifier getTexture() {
      return this.texture;
   }
}