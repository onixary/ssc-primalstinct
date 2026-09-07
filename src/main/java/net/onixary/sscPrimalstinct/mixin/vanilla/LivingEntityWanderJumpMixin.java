package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.LivingEntity;
import net.onixary.sscPrimalstinct.instinct.WanderJumpPhysics;
import net.onixary.sscPrimalstinct.instinct.WanderJumpSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityWanderJumpMixin implements WanderJumpSettings {
    @Unique
    private float primalstinct$jumpHeightMultiplier = 1.0f;
    @Unique private boolean primalstinct$isWanderProxy;
    @Unique private double primalstinct$pendingJump = Double.NaN;

    @Override
    public void primalstinct$setJumpHeightMultiplier(float multiplier) {
        primalstinct$jumpHeightMultiplier = multiplier;
        primalstinct$isWanderProxy = true;
    }

    @Override
    public double primalstinct$consumeJumpVelocity() {
        double velocity = primalstinct$pendingJump;
        primalstinct$pendingJump = Double.NaN;
        return velocity;
    }

    @Inject(method = "jump", at = @At("TAIL"))
    private void primalstinct$captureJump(CallbackInfo ci) {
        if (primalstinct$isWanderProxy) {
            primalstinct$pendingJump = ((LivingEntity) (Object) this).getVelocity().y;
        }
    }

    @Inject(method = "getJumpVelocity", at = @At("RETURN"), cancellable = true)
    private void primalstinct$scaleProxyJump(CallbackInfoReturnable<Float> cir) {
        if (primalstinct$jumpHeightMultiplier != 1.0f) {
            cir.setReturnValue(WanderJumpPhysics.scaleHeight(cir.getReturnValueF(),
                    primalstinct$jumpHeightMultiplier));
        }
    }
}
