package wtf.wyvern.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import wtf.wyvern.base.events.impl.render.EventAspectRatio;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(
        name = "AspectRatio",
        category = Category.RENDER,
        description = "Изменяет соотношение сторон экрана"
)
public class AspectRatio extends Module {
    public static final AspectRatio INSTANCE = new AspectRatio();

    private final NumberSetting ratio = new NumberSetting("Соотношение", 1.78F, 0.5F, 4.0F, 0.01F);

    private AspectRatio() {
    }

    @EventTarget
    private void onAspectRatio(EventAspectRatio event) {
        event.setRatio(ratio.getCurrent());
        event.setCancelled(true);
    }
}