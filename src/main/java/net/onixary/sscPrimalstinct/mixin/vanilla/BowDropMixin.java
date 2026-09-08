package net.onixary.sscPrimalstinct.mixin.vanilla;
import net.minecraft.item.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.interaction.ToolDropHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BowItem.class)
public abstract class BowDropMixin {
    @Inject(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;incrementStat(Lnet/minecraft/stat/Stat;)V", shift = At.Shift.AFTER))
    private void primalstinct$shot(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        ToolDropHelper.dropFiredWeapon(user, stack);
    }
}
