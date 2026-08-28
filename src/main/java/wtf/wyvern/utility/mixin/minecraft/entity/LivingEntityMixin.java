package wtf.wyvern.utility.mixin.minecraft.entity;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import wtf.wyvern.utility.interfaces.IMinecraft;

@Mixin({LivingEntity.class})
public class LivingEntityMixin implements IMinecraft {
}