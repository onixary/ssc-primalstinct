package net.onixary.sscPrimalstinct.mixin.ssc;

import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡04：切断旧本能计算（MIXIN_INVENTORY.md S1/S2）。
 * 安装本附属即全局重构：旧 serverTick（含增长、诅咒之月冻结、满值 checkThreshold→变身→清零）
 * 与旧效果写入（clearInstinct / addInstinctEffect 两个重载，处理器不捕获目标参数即可同时覆盖）
 * 全部废弃；新值唯一来源是 ssc-primalstinct:primalstinct 组件（卡03），
 * 新服务端 tick 见 instinct/PrimalstinctTicker。
 *
 * 卡15：同时切断旧客户端 clientTick——废弃旧值外推计数与高值预警粒子路径
 * （旧值已冻结，存量残值会导致幽灵粒子/旧条虚影；新粒子见 client/effect/PrimalstinctWarningParticles）。
 *
 * 目标是 SSC 自有类：类级 remap=false + 仅方法名匹配（SSC 自身成员名在生产产物中不变；
 * 参照 true-feral-addon-modifier 对附属类注入的生产验证模式）。
 * 不修改普通金苹果/牛奶的原版行为：SSC ItemStackMixin 不经过这些方法，
 * 旧的“金苹果抑制本能”路径因写入被废弃而自然失效。
 */
@Mixin(value = InstinctUtils.class, remap = false)
public abstract class InstinctUtilsMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$cutServerTick(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clearInstinct", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$cutClearInstinct(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "addInstinctEffect", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$cutAddInstinctEffect(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$cutClientTick(CallbackInfo ci) {
        ci.cancel();
    }
}
