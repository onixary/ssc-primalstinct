package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.onixary.sscPrimalstinct.interaction.InteractionRestrictions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void primalstinct$denyPrediction(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                             CallbackInfoReturnable<ActionResult> cir) {
        if (InteractionRestrictions.blocksInteraction(player, hand, hit)) cir.setReturnValue(ActionResult.FAIL);
    }
}
