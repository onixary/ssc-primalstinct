package net.onixary.sscPrimalstinct.mixin.vanilla;
import net.minecraft.item.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.interaction.ToolDropHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CrossbowItem.class)
public abstract class CrossbowDropMixin {
    @Inject(method = "postShoot", at = @At("TAIL"))
    private static void primalstinct$shot(World world, LivingEntity user, ItemStack stack, CallbackInfo ci) {
        ToolDropHelper.dropFiredWeapon(user, stack);
    }
}
