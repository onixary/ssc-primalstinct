package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.onixary.sscPrimalstinct.inventory.InventoryLockManager;
import net.onixary.sscPrimalstinct.inventory.InventoryLockRule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

/**
 * 卡09（MIXIN_INVENTORY.md V1）：Slot 层闸门。
 * canInsert=false 挡住一切经 ScreenHandler 的物品流入锁定槽（快移/拖拽放置/容器转移）；
 * canTakeItems=false 挡住从锁定槽取出（普通点击/双击收集/快移源）。
 * 仅作用于 PlayerInventory 的 0–35 槽；盔甲/副手/其他容器不受影响。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    @Final
    public Inventory inventory;

    @Shadow
    public abstract int getIndex();

    private @Nullable InventoryLockRule primalstinct$rule() {
        if (!(this.inventory instanceof PlayerInventory playerInventory)
                || !(playerInventory.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return null;
        }
        return InventoryLockManager.ruleIfRestricted(serverPlayer);
    }

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockInsertIntoLocked(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        InventoryLockRule rule = primalstinct$rule();
        if (rule != null && rule.isLocked(this.getIndex())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockTakeFromLocked(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        // PICKUP_ALL can inspect the result slot while the clicked slot is elsewhere.
        if (this.inventory instanceof net.minecraft.inventory.CraftingResultInventory
                && net.onixary.sscPrimalstinct.interaction.InteractionRestrictions.blocksCrafting(
                        player, player.currentScreenHandler)) {
            cir.setReturnValue(false);
            return;
        }
        InventoryLockRule rule = primalstinct$rule();
        if (rule != null && rule.isLocked(this.getIndex())) {
            cir.setReturnValue(false);
        }
    }
}
