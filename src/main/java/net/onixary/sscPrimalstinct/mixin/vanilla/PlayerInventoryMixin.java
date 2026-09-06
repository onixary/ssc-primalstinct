package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.onixary.sscPrimalstinct.inventory.InventoryLockManager;
import net.onixary.sscPrimalstinct.inventory.InventoryLockRule;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡09（MIXIN_INVENTORY.md V1）：PlayerInventory 插入路径拦截（捡物、/give、正规模组塞物）。
 * insertStack(ItemStack) 重定向到"仅允许槽"的定向插入；insertStack(slot, stack) 对锁定槽直接失败。
 * offer/offerOrDrop 内部复用 insertStack，随之覆盖。直接 setStack 的旁路在规则变化时被
 * InventoryLockManager 的搬移逻辑收编（不做逐 tick 扫描）。
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    public PlayerEntity player;

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void primalstinct$insertIntoAllowedOnly(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return;
        }
        InventoryLockRule rule = InventoryLockManager.ruleIfRestricted(serverPlayer);
        if (rule == null) {
            return;
        }
        PlayerInventory self = (PlayerInventory) (Object) this;
        ItemStack remainder = InventoryLockManager.insertIntoAllowed(self, rule, stack.copy());
        stack.setCount(remainder.getCount());
        cir.setReturnValue(remainder.isEmpty());
    }

    @Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockLockedSlotInsert(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(this.player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return;
        }
        InventoryLockRule rule = InventoryLockManager.ruleIfRestricted(serverPlayer);
        if (rule != null && rule.isLocked(slot)) {
            cir.setReturnValue(false);
        }
    }
}
