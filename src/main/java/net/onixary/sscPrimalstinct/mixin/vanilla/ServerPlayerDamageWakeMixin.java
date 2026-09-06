package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.sleep.CurlSleepController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡11：实际受伤后退出蜷缩；免疫/被拒绝的伤害不触发。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerDamageWakeMixin {

    @Inject(method = "damage", at = @At("RETURN"))
    private void primalstinct$wakeOnDamage(DamageSource source, float amount,
                                           CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (cir.getReturnValueZ()) {
            CurlSleepController.wakeUp(self, "damage");
        }
    }
}
