package net.onixary.sscPrimalstinct.inventory;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.power.factory.RestrictHotbarPower;
import net.onixary.sscPrimalstinct.power.factory.RestrictInventoryPower;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卡09：锁槽规则的派生、搬移与暂存（全部服务端主线程；搬移只发生在规则变化时，不逐 tick 扫描）。
 * 规则来源：restrict_hotbar / restrict_inventory Power（活跃实例取 max 合并）。
 * 升级/降级守恒：锁定槽内物品先入可用槽、余者按原槽索引暂存；解锁优先还原原槽；
 * 光标持物在收紧前先保存合法状态；绝不覆盖已有物品、绝不自动扔出（死亡除外，见下）。
 * 死亡规则：keepInventory=false → 暂存于死亡位置掉落一次并清空（禁止免死仓库）；
 * keepInventory=true → 随组件 ALWAYS_COPY 保留。Trinkets 槽不在管理范围，完全保留。
 */
public final class InventoryLockManager {

    private static final Map<UUID, InventoryLockRule> RULES = new ConcurrentHashMap<>();

    private InventoryLockManager() {
    }

    public static void init() {
        // JOIN 走 updateRule：派生-搬移-同步一体（客户端紧凑快捷栏立即拿到规则；
        // 返回玩家若在锁定槽残留物品也在此收编）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                updateRule(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                RULES.remove(handler.player.getUuid()));
        // 死亡：非 keepInventory 时暂存掉落一次并清空（先于重生复制，避免复制）
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
                List<PrimalstinctComponent.StashEntry> stash = component.getStash();
                if (!stash.isEmpty() && !player.getWorld().getGameRules().getBoolean(
                        net.minecraft.world.GameRules.KEEP_INVENTORY)) {
                    for (PrimalstinctComponent.StashEntry entry : new ArrayList<>(stash)) {
                        net.minecraft.entity.ItemEntity drop = player.dropItem(entry.stack().copy(), false, false);
                        if (drop != null) {
                            drop.setPickupDelay(40);
                        }
                    }
                    component.clearStash();
                    SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 死亡，暂存物品已在死亡位置掉落并清空",
                            player.getGameProfile().getName());
                }
            }
            return true;
        });
    }

    /** Mixin 热路径：受限则返回规则，未受限返回 null（含无数据/非玩家情形）。 */
    public static @Nullable InventoryLockRule ruleIfRestricted(@Nullable ServerPlayerEntity player) {
        if (player == null || !net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) {
            return null;
        }
        InventoryLockRule rule = RULES.get(player.getUuid());
        return rule != null && rule.isRestricted() ? rule : null;
    }

    public static @Nullable InventoryLockRule rule(@Nullable ServerPlayerEntity player) {
        return player == null ? null : RULES.get(player.getUuid());
    }

    /** 由 PrimalPowerReconciler 在 Power 结算后调用：规则变化时执行搬移/暂存/还原。 */
    public static void updateRule(ServerPlayerEntity player) {
        InventoryLockRule newRule = deriveRule(player);
        InventoryLockRule oldRule = RULES.getOrDefault(player.getUuid(), InventoryLockRule.UNRESTRICTED);
        if (newRule.equals(oldRule) && RegPrimalstinctComponent.PRIMALSTINCT.get(player).getStash().isEmpty()) {
            RULES.put(player.getUuid(), newRule);
            return;
        }
        applyRuleChange(player, oldRule, newRule);
        RULES.put(player.getUuid(), newRule);
        player.currentScreenHandler.sendContentUpdates();
        // 规则变化即时同步（客户端紧凑快捷栏/占位渲染依赖）
        net.onixary.sscPrimalstinct.network.PrimalstinctNetwork.syncNow(player);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 库存规则更新：快捷栏 {}/{}，主背包 {}/{}",
                player.getGameProfile().getName(),
                newRule.allowedHotbarSlots(), InventoryLockRule.HOTBAR_SIZE,
                newRule.allowedMainSlots(), InventoryLockRule.MAIN_SIZE);
    }

    /**
     * 派生规则：0–35 各维度独立取活跃 Power 的最大允许值（升级放宽）；
     * 盔甲/副手锁按 OR 合并（任一活跃 lock Power 生效，且只能经等级 remove 显式解除）。
     */
    private static InventoryLockRule deriveRule(ServerPlayerEntity player) {
        if (!net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) return InventoryLockRule.UNRESTRICTED;
        int hotbar = -1;
        int main = -1;
        for (RestrictHotbarPower power : PowerHolderComponent.getPowers(player, RestrictHotbarPower.class)) {
            if (power.isActive()) {
                hotbar = Math.max(hotbar, power.getAllowedSlots());
            }
        }
        for (RestrictInventoryPower power : PowerHolderComponent.getPowers(player, RestrictInventoryPower.class)) {
            if (power.isActive()) {
                main = Math.max(main, power.getAllowedSlots());
            }
        }
        boolean lockOffhand = !PowerHolderComponent.getPowers(
                player, net.onixary.sscPrimalstinct.power.factory.LockOffhandPower.class).isEmpty();
        return new InventoryLockRule(
                hotbar < 0 ? InventoryLockRule.HOTBAR_SIZE : hotbar,
                main < 0 ? InventoryLockRule.MAIN_SIZE : main,
                false, lockOffhand);
    }

    private static void applyRuleChange(ServerPlayerEntity player, InventoryLockRule oldRule, InventoryLockRule newRule) {
        PlayerInventory inv = player.getInventory();
        PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);

        // 1) 光标持物：收紧前先保存合法状态（可用槽 → 暂存，槽标记 -1）
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (!cursor.isEmpty() && !newRule.allowsAnyInsert()) {
            ItemStack remainder = insertIntoAllowed(inv, newRule, cursor.copy());
            if (!remainder.isEmpty()) {
                component.stashItem(-1, remainder);
            }
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        }

        // 2) 锁定槽内物品：先入可用槽，余者按原槽索引暂存（不自动扔出）
        for (int slot = 0; slot < InventoryLockRule.HOTBAR_SIZE + InventoryLockRule.MAIN_SIZE; slot++) {
            if (!newRule.isLocked(slot)) {
                continue;
            }
            ItemStack stack = inv.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            inv.setStack(slot, ItemStack.EMPTY);
            ItemStack remainder = insertIntoAllowed(inv, newRule, stack);
            if (!remainder.isEmpty()) {
                component.stashItem(slot, remainder);
            }
        }

        // 2.5) 卡10：新锁定的盔甲/副手——清空原槽与原地掉落成对、只执行一次，短拾取延迟防循环
        for (int slot = InventoryLockRule.ARMOR_START; slot <= InventoryLockRule.OFFHAND_SLOT; slot++) {
            if (!newRule.isLocked(slot)) {
                continue;
            }
            ItemStack stack = inv.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            inv.setStack(slot, ItemStack.EMPTY);
            net.minecraft.entity.ItemEntity drop = player.dropItem(stack, false, false);
            if (drop != null) {
                drop.setPickupDelay(40);
            }
        }

        // 3) 解锁还原：优先原槽（空则放回），否则尝试可用槽，仍放不下继续暂存
        Iterator<PrimalstinctComponent.StashEntry> iterator = component.getStash().iterator();
        List<PrimalstinctComponent.StashEntry> unresolved = new ArrayList<>();
        while (iterator.hasNext()) {
            PrimalstinctComponent.StashEntry entry = iterator.next();
            if (entry.slot() < 0) {
                ItemStack remainder = insertIntoAllowed(inv, newRule, entry.stack());
                iterator.remove();
                if (!remainder.isEmpty()) unresolved.add(new PrimalstinctComponent.StashEntry(-1, remainder));
                continue;
            }
            if (newRule.isLocked(entry.slot())) {
                unresolved.add(entry);
                iterator.remove();
                continue;
            }
            if (inv.getStack(entry.slot()).isEmpty()) {
                inv.setStack(entry.slot(), entry.stack());
                iterator.remove();
            } else {
                ItemStack remainder = insertIntoAllowed(inv, newRule, entry.stack());
                if (remainder.isEmpty()) {
                    iterator.remove();
                } else {
                    iterator.remove();
                    unresolved.add(new PrimalstinctComponent.StashEntry(entry.slot(), remainder));
                }
            }
        }
        for (PrimalstinctComponent.StashEntry entry : unresolved) {
            component.stashItem(entry.slot(), entry.stack());
        }
        player.currentScreenHandler.sendContentUpdates();
    }

    /** 允许槽位内的定向插入：先叠加同类未满，再空槽；快捷栏优先于主背包（贴近原版手感）。 */
    public static ItemStack insertIntoAllowed(PlayerInventory inv, InventoryLockRule rule, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!rule.allowsAnyInsert()) {
            return stack;
        }
        // 第一遍：叠加
        for (int slot : orderedAllowedSlots(rule)) {
            ItemStack target = inv.getStack(slot);
            if (!target.isEmpty() && ItemStack.canCombine(target, stack)
                    && target.getCount() < target.getMaxCount()) {
                int room = target.getMaxCount() - target.getCount();
                int moved = Math.min(room, stack.getCount());
                target.increment(moved);
                stack.decrement(moved);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        // 第二遍：空槽
        for (int slot : orderedAllowedSlots(rule)) {
            if (inv.getStack(slot).isEmpty()) {
                inv.setStack(slot, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private static List<Integer> orderedAllowedSlots(InventoryLockRule rule) {
        List<Integer> slots = new ArrayList<>(rule.allowedHotbarSlots() + rule.allowedMainSlots());
        for (int i = 0; i < rule.allowedHotbarSlots(); i++) {
            slots.add(i);
        }
        for (int i = 0; i < rule.allowedMainSlots(); i++) {
            slots.add(InventoryLockRule.HOTBAR_SIZE + i);
        }
        return slots;
    }
}
