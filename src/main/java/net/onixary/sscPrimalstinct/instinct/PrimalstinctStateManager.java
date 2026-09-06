package net.onixary.sscPrimalstinct.instinct;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;
import org.jetbrains.annotations.NotNull;

/**
 * 卡03：状态访问门面——唯一权威数据源是 CCA 组件（ssc-primalstinct:primalstinct）。
 * 卡01 的内存 Map 占位已被替换；value/locked 持久化、level 派生。
 */
public final class PrimalstinctStateManager {

    private PrimalstinctStateManager() {
    }

    public static @NotNull PrimalstinctComponent component(ServerPlayerEntity player) {
        return RegPrimalstinctComponent.PRIMALSTINCT.get(player);
    }

    /**
     * reconcile（卡05 内核落地前的骨架语义）：钳制数值、修复满值不变量并同步。
     *
     * @return 是否发生了变更
     */
    public static boolean reconcile(ServerPlayerEntity player) {
        PrimalstinctComponent component = component(player);
        float beforeValue = component.getValue();
        boolean beforeLocked = component.isLocked();

        component.setValue(component.getValue());
        component.repairInvariants();

        boolean changed = beforeValue != component.getValue() || beforeLocked != component.isLocked();
        if (changed) {
            PrimalstinctNetwork.syncNow(player);
        }
        return changed;
    }
}
