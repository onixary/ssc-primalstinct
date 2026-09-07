package net.onixary.sscPrimalstinct.mixin.ssc.client;

import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InstinctUtils.class, remap = false)
public abstract class InstinctClientTickMixin {
    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$legacyTick(CallbackInfo ci) {
        if (ClientPrimalstinctState.suppressLegacy()) ci.cancel();
    }
}
