package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.sleep.CurlSleepState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCurlSleepMixin {
    @Inject(method = "isSleepingInBed", at = @At("HEAD"), cancellable = true)
    private void primalstinct$allowGroundSleep(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CurlSleepState state && state.primalstinct$isCurled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setPositionInBed", at = @At("HEAD"), cancellable = true)
    private void primalstinct$keepGroundPosition(BlockPos pos, CallbackInfo ci) {
        if ((Object) this instanceof CurlSleepState state && state.primalstinct$isCurled()) ci.cancel();
    }
}
