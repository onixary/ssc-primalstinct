package net.onixary.sscPrimalstinct.mixin.ssc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.sscPrimalstinct.power.PrimalPowerReconciler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SSC can rebuild powers without emitting FORM_CHANGE_END (e.g. perk reset). */
@Mixin(value = FormUtils.class, remap = false)
public abstract class FormUtilsMixin {
    @Inject(method = "_loadForm", at = @At("RETURN"))
    private static void primalstinct$refreshFeralScale(PlayerEntity player,
            net.onixary.shapeShifterCurseFabric.player_form.IForm form, boolean reload, CallbackInfo ci) {
        net.onixary.sscPrimalstinct.feral.RegFeralComponent.FERAL.get(player).refreshScale();
    }

    @Inject(method = "reApplyPower", at = @At("RETURN"))
    private static void primalstinct$reapplyMasks(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            net.onixary.sscPrimalstinct.feral.RegFeralComponent.FERAL.get(player).refreshScale();
            PrimalPowerReconciler.requestReconcile(serverPlayer);
        }
    }
}
