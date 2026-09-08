package net.onixary.sscPrimalstinct.mixin.vanilla;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.sscPrimalstinct.power.factory.PassiveUntilProvokedPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetPredicate.class)
public abstract class TargetPredicateMixin {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockUnprovokedAggro(LivingEntity baseEntity, LivingEntity targetEntity,
                                                   CallbackInfoReturnable<Boolean> cir) {
        // lastAttacker 检查先行（廉价），Power 查询只对生物→玩家判定发生；
        // 受击后 lastAttacker 指向攻击者，RevengeGoal 的还击路径由此放行。
        if (baseEntity instanceof MobEntity mob
                && targetEntity instanceof PlayerEntity player
                && mob.getAttacker() != player
                && PowerHolderComponent.hasPower(player, PassiveUntilProvokedPower.class)) {
            cir.setReturnValue(false);
        }
    }
}
