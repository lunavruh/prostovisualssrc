package wtf.wyvern.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.events.impl.render.EventRender3D;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.BooleanSetting;
import wtf.wyvern.client.modules.api.setting.impl.NumberSetting;
import wtf.wyvern.utility.render.level.Render3DUtil;

@ModuleAnnotation(
        name = "CustomHitBox",
        category = Category.RENDER,
        description = "Кастомные хитбоксы игроков и мобов"
)
public final class CustomHitBox extends Module {
    public static final CustomHitBox INSTANCE = new CustomHitBox();

    public final BooleanSetting players = new BooleanSetting("Players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", true);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 1.5f, 0.5f, 6.0f, 0.1f);

    private CustomHitBox() {}

    @EventTarget
    private void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        int color = Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB();
        float tickDelta = event.getPartialTicks();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || entity.isInvisible()) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            if (entity instanceof PlayerEntity) {
                if (!players.isEnabled()) continue;
            } else if (!mobs.isEnabled()) {
                continue;
            }

            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Vec3d current = entity.getPos();
            Vec3d delta = lerped.subtract(current);
            Box box = entity.getBoundingBox().offset(delta).expand(0.002);
            Render3DUtil.drawBox(box, color, lineWidth.getCurrent(), true, fill.isEnabled(), true);
        }
    }
}
