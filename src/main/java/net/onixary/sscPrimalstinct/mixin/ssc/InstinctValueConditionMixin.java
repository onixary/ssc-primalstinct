package net.onixary.sscPrimalstinct.mixin.ssc;

import net.onixary.shapeShifterCurseFabric.additional_power.InstinctValueCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡04/卡17（MIXIN_INVENTORY.md S5）：旧 instinct_value 条件按玩家分流。
 * 接管态（受管理或 pending，见 PrimalstinctLifecycle.suppressLegacy）返回 false——
 * 旧字段（PlayerFormComponent.instinctValue）不作为新系统输入，附属能力一律用新等级条件（卡06/07）；
 * 普通路线（未接管）透传 SSC 原判定，旧 Power（蜘蛛满值粒子等）照常工作。
 * 目标为 SSC 自有类：类级 remap=false + 仅方法名匹配。
 */
@Mixin(value = InstinctValueCondition.class, remap = false)
public abstract class InstinctValueConditionMixin {

    @Inject(method = "condition", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$disableOldCondition(io.github.apace100.calio.data.SerializableData.Instance data,
                                                        net.minecraft.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity player
                && net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.suppressLegacy(player)) cir.setReturnValue(false);
    }
}
