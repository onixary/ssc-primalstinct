package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.onixary.sscPrimalstinct.power.factory.PreventVillagerTradePower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerTradeMixin {
    @Shadow private void sayNo() { throw new AssertionError(); }

    // Only reached for an adult, awake, available villager with actual trade offers.
    @Inject(method = "beginTradeWith", at = @At("HEAD"), cancellable = true)
    private void primalstinct$refuseTrade(PlayerEntity customer, CallbackInfo ci) {
        if (PreventVillagerTradePower.applies(customer)) {
            sayNo();
            customer.sendMessage(Text.translatable("message.ssc-primalstinct.villager_refuses_trade"), true);
            ci.cancel();
        }
    }
}
