package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import net.minecraft.client.resource.language.I18n;

public class ItemPhysic extends Module {
    public ItemPhysic() {
        super("ItemPhysic", Category.Render, I18n.translate("module.itemphysic.description"));
    }
}
