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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锁槽规则派生与掉落（服务端主线程，规则变化时清空锁定槽）。
 * 规则来源：restrict_hotbar / restrict_inventory Power（活跃实例取 max 合并）。
 * 锁定槽的真实物品掉落到玩家身边，不搬移或暂存；旧版本暂存物品一次性释放。
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

    /** Power 结算后更新规则并掉落锁定槽和旧暂存区物品。 */
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
                PowerHolderComponent.getPowers(player, RestrictInventoryPower.class).stream()
                        .anyMatch(p -> p.isActive() && p.lockEquipment), lockOffhand);
    }

    private static void applyRuleChange(ServerPlayerEntity player, InventoryLockRule oldRule, InventoryLockRule newRule) {
        PlayerInventory inv = player.getInventory();
        PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);

        // Clear the actual stack before spawning it; never copy or hide locked items.
        if (!newRule.allowsAnyInsert()) {
            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            dropBesidePlayer(player, cursor);
        }
        for (int slot = 0; slot <= InventoryLockRule.OFFHAND_SLOT; slot++) {
            if (!newRule.isLocked(slot)) continue;
            ItemStack stack = inv.getStack(slot);
            inv.setStack(slot, ItemStack.EMPTY);
            dropBesidePlayer(player, stack);
        }
        // Release legacy stashes once, including items hidden before this update.
        var stored = new ArrayList<>(component.getStash());
        component.getStash().clear();
        for (var entry : stored) dropBesidePlayer(player, entry.stack());
        inv.markDirty();
        player.playerScreenHandler.syncState();
        if (player.currentScreenHandler != player.playerScreenHandler) player.currentScreenHandler.syncState();
    }

    private static void dropBesidePlayer(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        var drop = player.dropItem(stack, false, false);
        if (drop != null) drop.setPickupDelay(40);
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
