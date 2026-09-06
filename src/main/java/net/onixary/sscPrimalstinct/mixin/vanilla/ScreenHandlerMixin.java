package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;
import net.onixary.sscPrimalstinct.inventory.InventoryLockManager;
import net.onixary.sscPrimalstinct.inventory.InventoryLockRule;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡09（MIXIN_INVENTORY.md V1）：服务端点击级防御（internalOnSlotClick 为服务端唯一入口）。
 * 覆盖：普通点击/快移/数字键交换（含按钮槽）/丢弃/创造中键/双击收集作用在锁定槽；
 * QUICK_CRAFT 拖拽在 stage 1（逐槽加入拖拽集，slotIndex 即目标槽）拦截锁定槽，
 * stage 2 只作用于已过滤的拖拽集。Slot 层闸门（SlotMixin）作为容器转移/拖拽放置的第二道防线。
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Nullable
    private static InventoryLockRule primalstinct$rule(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return null;
        }
        return InventoryLockManager.ruleIfRestricted(serverPlayer);
    }

    private static boolean primalstinct$isLockedPlayerSlot(Slot slot, InventoryLockRule rule) {
        return slot.inventory instanceof PlayerInventory && rule.isLocked(slot.getIndex());
    }

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void primalstinct$guardSlotClick(int slotIndex, int button, SlotActionType actionType,
                                             PlayerEntity player, CallbackInfo ci) {
        // Crafting is independent of inventory slot locks (including unrestricted L0).
        if (net.onixary.sscPrimalstinct.interaction.InteractionRestrictions.blocksCrafting(
                player, (ScreenHandler) (Object) this)
                && slotIndex >= 0 && slotIndex < this.slots.size()
                && this.slots.get(slotIndex).inventory instanceof net.minecraft.inventory.CraftingResultInventory) {
            ci.cancel();
            if (player instanceof ServerPlayerEntity sp) sp.currentScreenHandler.syncState();
            return;
        }
        InventoryLockRule rule = primalstinct$rule(player);
        if (rule == null) {
            return;
        }
        // 快捷栏数字键交换：button 为快捷栏索引，锁定槽不可参与
        if (actionType == SlotActionType.SWAP && button >= 0 && button < InventoryLockRule.HOTBAR_SIZE
                && rule.isLocked(button)) {
            ci.cancel();
            return;
        }
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return;  // -999（光标丢出窗口）等不属于槽位点击
        }
        Slot slot = this.slots.get(slotIndex);
        if (actionType == SlotActionType.QUICK_CRAFT) {
            // stage 1：把槽位加入拖拽集（slotIndex 即被加入槽）——锁定槽拒入
            if ((button & 3) == 1 && primalstinct$isLockedPlayerSlot(slot, rule)) {
                ci.cancel();
            }
            return;
        }
        if (primalstinct$isLockedPlayerSlot(slot, rule)) {
            ci.cancel();
        }
    }

}
