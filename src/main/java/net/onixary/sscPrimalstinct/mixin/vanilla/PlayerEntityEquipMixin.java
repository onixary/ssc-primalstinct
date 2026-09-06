package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.inventory.InventoryLockManager;
import net.onixary.sscPrimalstinct.inventory.InventoryLockRule;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡10（V2）：右键穿戴/发射器装备等绕过 GUI 的装备路径——锁定的盔甲/副手槽
 * 拒绝 equipStack，物品原地掉落（清空与掉落成对、只执行一次、短拾取延迟）。
 * 注意注入 PlayerEntity 的具体实现（LivingEntity 声明为抽象方法，无方法体可注入）。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityEquipMixin {

    @Inject(method = "equipStack", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockLockedEquip(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity player) || self.getWorld().isClient()) {
            return;
        }
        @Nullable InventoryLockRule rule = InventoryLockManager.ruleIfRestricted(player);
        if (rule == null) {
            return;
        }
        int invIndex = switch (slot) {
            case FEET -> 36;
            case LEGS -> 37;
            case CHEST -> 38;
            case HEAD -> 39;
            case OFFHAND -> 40;
            default -> -1;
        };
        if (invIndex >= 0 && rule.isLocked(invIndex) && !stack.isEmpty()) {
            ci.cancel();
            var drop = player.dropItem(stack, false, false);
            if (drop != null) {
                drop.setPickupDelay(40);
            }
        }
    }
}
