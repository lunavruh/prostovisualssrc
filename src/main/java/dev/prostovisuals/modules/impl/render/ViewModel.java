package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.api.Category;
import net.minecraft.client.resource.language.I18n;

public class ViewModel extends Module {

    public NumberSetting mainX = new NumberSetting("setting.mainX", 0f, -2f, 2f, 0.05f);
    public NumberSetting mainY = new NumberSetting("setting.mainY", 0f, -2f, 2f, 0.05f);
    public NumberSetting mainZ = new NumberSetting("setting.mainZ", 0f, -2f, 2f, 0.05f);
    public NumberSetting offX = new NumberSetting("setting.offX", 0f, -2f, 2f, 0.05f);
    public NumberSetting offY = new NumberSetting("setting.offY", 0f, -2f, 2f, 0.05f);
    public NumberSetting offZ = new NumberSetting("setting.offZ", 0f, -2f, 2f, 0.05f);
    public ViewModel() {
        super("ViewModel", Category.Render, I18n.translate("module.viewmodel.description"));
    }
}