package net.onixary.sscPrimalstinct.mixin.vanilla;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.onixary.sscPrimalstinct.interaction.ToolDropHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** postHit is called only after accepted melee damage, and after durability is applied. */
@Mixin(ItemStack.class)
public abstract class WeaponHitDropMixin {
    @Inject(method = "postHit", at = @At("TAIL"))
    private void primalstinct$dropWeaponAfterHit(LivingEntity target, PlayerEntity attacker, CallbackInfo ci) {
        if (attacker instanceof ServerPlayerEntity player && player.getMainHandStack() == (Object)this)
            ToolDropHelper.dropHeldTool(player, Hand.MAIN_HAND, true);
    }
}
