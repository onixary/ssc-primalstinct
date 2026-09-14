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
    // Keep the outer interactBlock call: it sends the sequenced packet even when
    // local prediction is suppressed, so server rolls, messages and actions still run.
    @Inject(method = "interactBlockInternal", at = @At("HEAD"), cancellable = true)
    private void primalstinct$denyPrediction(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                             CallbackInfoReturnable<ActionResult> cir) {
        if (InteractionRestrictions.blocksInteraction(player, hand, hit)
                || InteractionRestrictions.waitForDoorInteraction(player, hit)) {
            cir.setReturnValue(ActionResult.CONSUME);
        }
    }
}
