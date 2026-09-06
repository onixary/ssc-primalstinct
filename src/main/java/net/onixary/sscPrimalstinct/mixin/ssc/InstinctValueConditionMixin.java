package net.onixary.sscPrimalstinct.mixin.ssc;

import net.onixary.shapeShifterCurseFabric.additional_power.InstinctValueCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡04：旧 instinct_value 条件在附属模式下恒为 false（MIXIN_INVENTORY.md S5）。
 * 旧字段（PlayerFormComponent.instinctValue）不再作为任何新系统输入；
 * 引用该条件的 Power（如蜘蛛满值粒子等）随之停用，待卡06/07 以新等级条件重建。
 * 目标为 SSC 自有类：类级 remap=false + 仅方法名匹配。
 */
@Mixin(value = InstinctValueCondition.class, remap = false)
public abstract class InstinctValueConditionMixin {

    @Inject(method = "condition", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$disableOldCondition(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
