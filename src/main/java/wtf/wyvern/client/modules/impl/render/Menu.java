package wtf.wyvern.client.modules.impl.render;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.client.ui.clickgui.ClickGui;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;

@ModuleAnnotation(name = "ClickGUI", category = Category.RENDER, description = "ProstoVisual ClickGUI")
public final class Menu extends Module {
    public static final Menu INSTANCE = new Menu();

    private Menu() { this.setKeyCode(344); }

    @Override
    public void onEnable() {
        if (mc.world == null || prostovisuals.getInstance() == null || prostovisuals.getInstance().getClickGui() == null) {
            this.setEnabled(false);
            return;
        }
        if (!(mc.currentScreen instanceof ClickGui)) {
            mc.setScreen(prostovisuals.getInstance().getClickGui());
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof ClickGui gui) gui.close();
        super.onDisable();
    }

    @Override
    public void setKeyCode(int keyCode) { if (keyCode != -1) super.setKeyCode(keyCode); }
}
