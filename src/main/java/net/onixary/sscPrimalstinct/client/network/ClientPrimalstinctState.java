package net.onixary.sscPrimalstinct.client.network;

import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;
import org.jetbrains.annotations.Nullable;

/**
 * 卡03：客户端侧最新快照。仅用于 UI 展示（条形长度可插值/外推）；
 * 等级限制、掉落、解锁等规则一律使用服务端结果，客户端不自行以本地资源决定规则。
 */
public final class ClientPrimalstinctState {

    private static volatile @Nullable PrimalstinctStateS2C snapshot;
    private static volatile long clientTickReceived = -1;

    private ClientPrimalstinctState() {
    }

    public static void accept(PrimalstinctStateS2C payload, long clientTick) {
        snapshot = payload;
        clientTickReceived = clientTick;
    }

    public static @Nullable PrimalstinctStateS2C snapshot() {
        return snapshot;
    }

    public static long clientTickReceived() {
        return clientTickReceived;
    }

    /** 断线清理临时缓存（重进后由服务端快照重建）。 */
    public static void clear() {
        snapshot = null;
        clientTickReceived = -1;
    }

    // ---- 卡09 客户端锁槽渲染查询 ----

    public static int allowedHotbar() {
        PrimalstinctStateS2C s = snapshot;
        return s == null ? 9 : s.allowedHotbar();
    }

    public static int allowedMain() {
        PrimalstinctStateS2C s = snapshot;
        return s == null ? 27 : s.allowedMain();
    }

    public static boolean inventoryRestricted() {
        return allowedHotbar() < 9 || allowedMain() < 27;
    }

    /** PlayerInventory 索引（0–35）是否被锁定。 */
    public static boolean isLockedSlot(int playerInvIndex) {
        if (!inventoryRestricted()) {
            return false;
        }
        if (playerInvIndex < 0 || playerInvIndex >= 36) {
            return false;
        }
        return playerInvIndex < 9
                ? playerInvIndex >= allowedHotbar()
                : (playerInvIndex - 9) >= allowedMain();
    }
}
