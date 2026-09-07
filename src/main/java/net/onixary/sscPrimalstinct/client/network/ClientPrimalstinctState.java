package net.onixary.sscPrimalstinct.client.network;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;
import org.jetbrains.annotations.Nullable;

/**
 * 卡03：客户端侧最新快照。仅用于 UI 展示（条形长度可插值/外推）；
 * 等级限制、掉落、解锁等规则一律使用服务端结果，客户端不自行以本地资源决定规则。
 *
 * 卡15：外推与平滑纠正——
 * - 条长度按 value + rate × 经过时间外推并钳制 [0, maxValue]；收到新快照后向目标指数平滑纠正（τ≈0.12s）。
 * - 跨级提示、满值锁定样式以快照的 level/locked 为准（高延迟下客户端不得据预测提前解除锁定表现）。
 * - 断线/切世界（玩家实体更换）清理上一个世界的预测值，重新从服务端锚点出发。
 * 平滑字段仅在渲染线程读写（accept 经 client.execute 派发），不需要额外同步。
 */
public final class ClientPrimalstinctState {

    /** 平滑时间常数（秒）：值越小纠正越快。 */
    private static final double SMOOTHING_TAU_SECONDS = 0.12;

    private static volatile @Nullable PrimalstinctStateS2C snapshot;
    private static volatile long clientTickReceived = -1;
    /** 锚定的客户端玩家实体（age 作为外推时钟）；切世界后由 accept 重置。 */
    private static volatile @Nullable PlayerEntity anchorPlayer;

    // 以下仅渲染线程访问
    private static float smoothedValue = Float.NaN;
    private static long lastRenderNanos;

    private ClientPrimalstinctState() {
    }

    public static void accept(PrimalstinctStateS2C payload, @Nullable PlayerEntity player) {
        boolean playerChanged = player == null || anchorPlayer != player;
        boolean ownershipChanged = snapshot == null || snapshot.managed() != payload.managed()
                || snapshot.selectionPending() != payload.selectionPending();
        snapshot = payload;
        if (ownershipChanged) {
            resetPrediction();
            net.onixary.sscPrimalstinct.client.effect.PrimalstinctWarningParticles.reset();
            net.onixary.sscPrimalstinct.client.ui.PrimalInstinctHud.clearHint();
        }
        anchorPlayer = player;
        clientTickReceived = player == null ? 0L : player.age;
        if (playerChanged) {
            resetPrediction();
        }
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
        anchorPlayer = null;
        resetPrediction();
    }

    /** 丢弃平滑/外推中间态（断线、切世界、快照锚点重建时）。 */
    public static void resetPrediction() {
        smoothedValue = Float.NaN;
        lastRenderNanos = 0L;
    }

    // ---- 卡15 显示查询 ----

    /** 当前形态是否受本玩法管理（HUD 显隐；服务端判定，不读 SSC NoInstinct）。 */
    public static boolean suppressLegacy() {
        return snapshot != null && (snapshot.managed() || snapshot.selectionPending());
    }

    public static boolean managed() {
        PrimalstinctStateS2C s = snapshot;
        return s != null && s.managed();
    }

    public static float maxValue() {
        PrimalstinctStateS2C s = snapshot;
        return s == null ? 100.0f : s.maxValue();
    }

    /** 等级表阈值（升序，含满值）；无快照时退回内置默认。 */
    public static float[] thresholds() {
        PrimalstinctStateS2C s = snapshot;
        return s == null || s.thresholds().length == 0
                ? new float[]{20.0f, 40.0f, 60.0f, 80.0f, 100.0f}
                : s.thresholds();
    }

    /**
     * 显示值（条形长度语义）：外推 + 平滑纠正。仅在渲染线程调用（HUD）。
     * tick 线程（粒子）请使用 {@link #displayValueTick()}，不做帧间平滑。
     */
    public static float displayValue(PlayerEntity player, float tickDelta) {
        float target = extrapolate(player, tickDelta);
        if (Float.isNaN(target)) {
            return target;
        }
        long now = System.nanoTime();
        if (Float.isNaN(smoothedValue) || lastRenderNanos == 0L || now <= lastRenderNanos) {
            smoothedValue = target;
        } else {
            double dt = (now - lastRenderNanos) / 1.0e9;
            double k = 1.0 - Math.exp(-dt / SMOOTHING_TAU_SECONDS);
            smoothedValue += (target - smoothedValue) * (float) k;
        }
        lastRenderNanos = now;
        return smoothedValue;
    }

    /** tick 粒度的显示值（无帧间平滑）：粒子等非渲染线程使用。 */
    public static float displayValueTick(@Nullable PlayerEntity player) {
        return extrapolate(player, 0.0f);
    }

    private static float extrapolate(@Nullable PlayerEntity player, float tickDelta) {
        PrimalstinctStateS2C s = snapshot;
        if (s == null || player == null) {
            return Float.NaN;
        }
        float elapsedTicks = (player.age + tickDelta) - clientTickReceived;
        if (elapsedTicks < 0.0f) {
            elapsedTicks = 0.0f;
        }
        float raw = s.value() + s.rate() * (elapsedTicks / 20.0f);
        return Math.max(0.0f, Math.min(s.maxValue(), raw));
    }

    // ---- 卡09 客户端锁槽渲染查询 ----

    public static int allowedHotbar() {
        PrimalstinctStateS2C s = snapshot;
        return s == null || !s.managed() ? 9 : s.allowedHotbar();
    }

    public static int allowedMain() {
        PrimalstinctStateS2C s = snapshot;
        return s == null || !s.managed() ? 27 : s.allowedMain();
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
