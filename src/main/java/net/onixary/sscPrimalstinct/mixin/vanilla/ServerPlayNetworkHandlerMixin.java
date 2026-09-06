package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.interaction.InteractionRestrictions;
import net.onixary.sscPrimalstinct.inventory.InventoryLockManager;
import net.onixary.sscPrimalstinct.inventory.InventoryLockRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void primalstinct$rejectLockedSelection(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler) (Object) this, player.getServerWorld());
        InventoryLockRule rule = InventoryLockManager.ruleIfRestricted(player);
        if (rule != null && rule.isLocked(packet.getSelectedSlot())) {
            ci.cancel();
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(
                    player.getInventory().selectedSlot));
        }
    }

    // Block interactions are guarded in the interaction managers, never before packet sequence acknowledgement.
    @Inject(method = "onCraftRequest", at = @At("HEAD"), cancellable = true)
    private void primalstinct$guardRecipeBookTransfer(CraftRequestC2SPacket packet, CallbackInfo ci) {
        NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler) (Object) this, player.getServerWorld());
        if (InteractionRestrictions.blocksCrafting(player, player.currentScreenHandler)) {
            player.currentScreenHandler.syncState();
            ci.cancel();
        }
    }
}
