package wtf.wyvern.client.modules.api;

import lombok.Generated;

public enum Category {
   RENDER("Render", "m"),
   MISC("Misc", "q"),
   THEMES("Themes", "G");

   private final String name;
   private final String icon;

   private Category(String name, String icon) {
      this.name = name;
      this.icon = icon;
   }

   @Generated
   public String getIcon() {
      return this.icon;
   }

   @Generated
   public String getName() {
      return this.name;
   }

   private static Category[] $values() {
      return new Category[]{RENDER, MISC, THEMES};
   }
}