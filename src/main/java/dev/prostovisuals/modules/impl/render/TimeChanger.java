package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import net.minecraft.client.resource.language.I18n;

public class TimeChanger extends Module {
    public TimeChanger(){
        super("TimeChanger", Category.Render, I18n.translate("module.timechanger.description"));
    }
    public final NumberSetting Time = new NumberSetting("setting.time", 12f, 1f, 24f, 0.5f);
}
