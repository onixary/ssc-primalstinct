package net.onixary.sscPrimalstinct.mixin.ssc;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import net.onixary.sscPrimalstinct.feral.RegFeralComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FormTextureUtils.class, remap = false)
public abstract class FeralRenderFormMixin {
    @Inject(method = "getPlayerForm_Render", at = @At("RETURN"), cancellable = true)
    private static void primalstinct$presentation(PlayerEntity player, CallbackInfoReturnable<IForm> cir) {
        if (!FormTextureUtils.useTempFormModel) {
            cir.setReturnValue(RegFeralComponent.FERAL.get(player).renderForm(cir.getReturnValue()));
        }
    }
}
