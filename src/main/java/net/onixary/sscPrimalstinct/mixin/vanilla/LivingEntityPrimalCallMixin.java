package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.sscPrimalstinct.effect.PrimalCallEffect;
import net.onixary.sscPrimalstinct.instinct.PrimalCallService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityPrimalCallMixin {
    @Unique private boolean primalstinct$callExpired;

    @Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
    private void primalstinct$restrictCall(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect.getEffectType() == PrimalCallEffect.INSTANCE
                && !((LivingEntity) (Object) this).getWorld().isClient()
                && !PrimalCallService.eligible((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "onStatusEffectApplied", at = @At("TAIL"))
    private void primalstinct$announceCall(StatusEffectInstance effect, Entity source, CallbackInfo ci) {
        if (effect.getEffectType() == PrimalCallEffect.INSTANCE
                && (Object) this instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.translatable("chat.ssc-primalstinct.primal_call.started")
                    .formatted(Formatting.RED), false);
        }
    }

    @Redirect(method = "tickStatusEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/effect/StatusEffectInstance;update(Lnet/minecraft/entity/LivingEntity;Ljava/lang/Runnable;)Z"))
    private boolean primalstinct$detectExpiry(StatusEffectInstance effect, LivingEntity entity, Runnable callback) {
        boolean active = effect.update(entity, callback);
        if (!active && effect.getEffectType() == PrimalCallEffect.INSTANCE && effect.getDuration() == 0) {
            primalstinct$callExpired = true;
        }
        return active;
    }

    // Wait until vanilla has finished iterating effects before adding the stage effects.
    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
    private void primalstinct$finishCall(CallbackInfo ci) {
        if (primalstinct$callExpired) {
            primalstinct$callExpired = false;
            if ((Object) this instanceof ServerPlayerEntity player) PrimalCallService.awaken(player);
        }
    }
}
