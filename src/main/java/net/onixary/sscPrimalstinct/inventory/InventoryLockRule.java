package net.onixary.sscPrimalstinct.inventory;

import java.util.HashSet;
import java.util.Set;

/**
 * 卡09/10：玩家库存锁槽规则（纯函数，可单测）。
 * 索引语义与 PlayerInventory 一致：快捷栏 0–8，主背包 9–35，盔甲 36–39，副手 40。
 * 盔甲/副手由 lockEquipment/lockOffhand 独立开关（卡10），其余规则不变。
 * 多个 restrict Power 并存时各维度独立取 max（升级放宽）；锁类开关按 OR 合并。
 */
public record InventoryLockRule(int allowedHotbarSlots, int allowedMainSlots,
                                boolean lockEquipment, boolean lockOffhand) {

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 27;
    public static final int ARMOR_START = 36;
    public static final int OFFHAND_SLOT = 40;
    public static final InventoryLockRule UNRESTRICTED = new InventoryLockRule(HOTBAR_SIZE, MAIN_SIZE, false, false);

    public InventoryLockRule {
        allowedHotbarSlots = Math.max(0, Math.min(HOTBAR_SIZE, allowedHotbarSlots));
        allowedMainSlots = Math.max(0, Math.min(MAIN_SIZE, allowedMainSlots));
    }

    public boolean isRestricted() {
        return allowedHotbarSlots < HOTBAR_SIZE || allowedMainSlots < MAIN_SIZE
                || lockEquipment || lockOffhand;
    }

    /** player inventory 索引是否被锁定；本组件只管理 0–40 中的 0–35 与盔甲/副手。 */
    public boolean isLocked(int playerInvIndex) {
        if (playerInvIndex >= ARMOR_START && playerInvIndex < OFFHAND_SLOT) {
            return lockEquipment;
        }
        if (playerInvIndex == OFFHAND_SLOT) {
            return lockOffhand;
        }
        if (playerInvIndex < 0 || playerInvIndex >= HOTBAR_SIZE + MAIN_SIZE) {
            return false;
        }
        if (playerInvIndex < HOTBAR_SIZE) {
            return playerInvIndex >= allowedHotbarSlots;
        }
        return (playerInvIndex - HOTBAR_SIZE) >= allowedMainSlots;
    }

    /** 允许使用的槽位集合（0–35）。 */
    public Set<Integer> allowedSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < allowedHotbarSlots; i++) {
            slots.add(i);
        }
        for (int i = 0; i < allowedMainSlots; i++) {
            slots.add(HOTBAR_SIZE + i);
        }
        return slots;
    }

    public boolean allowsAnyInsert() {
        return allowedHotbarSlots > 0 || allowedMainSlots > 0;
    }
}
