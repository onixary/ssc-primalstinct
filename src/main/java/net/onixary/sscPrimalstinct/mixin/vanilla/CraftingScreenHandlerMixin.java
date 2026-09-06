package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.interaction.InteractionRestrictions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
    // Vanilla shares this method between the player's 2x2 grid and the crafting table.
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$clearForbiddenResult(ScreenHandler handler, World world,
            PlayerEntity player, RecipeInputInventory input, CraftingResultInventory result, CallbackInfo ci) {
        if (InteractionRestrictions.blocksCrafting(player, handler)) {
            result.setStack(0, ItemStack.EMPTY);
            if (player instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                        handler.syncId, handler.nextRevision(), 0, ItemStack.EMPTY));
            }
            ci.cancel();
        }
    }
}
