package net.onixary.sscPrimalstinct.instinct;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.network.LockCinematicS2C;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本能系统的服务端表现编排（表现向需求，2026-09-07）：
 * 1) 阈值前提示——value 到达下一阈值-2 且增速为正时，聊天栏提示一次（所有阈值共用一条）；
 *    离开接近窗口（跌回阈值-2 以下）后重置，再次接近可再提示。
 * 2) 非锁定升阶——1s 黑暗 + 5s 恶心（无粒子、保留图标）。
 * 3) 满值锁定——播放 SSC 变形屏幕效果时长（入段+出段）的锁定演出：
 *    客户端叠加层由附属包驱动（不经 SSC transformState，无变形动画与形态变更）；
 *    移动不做完全输入锁（noMove/noJump），改为 6s 缓慢 III，另叠 5s 黑暗 + 10s 恶心；
 *    演出结束后由服务端定时发送锁定 actionbar 文案（客户端不即时弹出，见 PrimalInstinctHud）。
 * 全部状态为运行期数据，断线随 Service.clearPlayer 清理。
 */
public final class PrimalstinctPresentation {

    /** 阈值接近窗口：距下一阈值 2 点内视为“即将到达”。 */
    private static final float THRESHOLD_APPROACH_WINDOW = 2.0f;
    private static final int DARKNESS_TICKS = 20;   // 升阶：1s
    private static final int NAUSEA_TICKS = 100;    // 升阶：5s
    private static final int LOCK_DARKNESS_TICKS = 100;   // 锁定演出：5s
    private static final int LOCK_NAUSEA_TICKS = 200;     // 锁定演出：10s
    private static final int LOCK_SLOWNESS_TICKS = 120;   // 锁定演出：6s 缓慢 III（不做完全输入锁）
    private static final int LOCK_SLOWNESS_AMPLIFIER = 4;

    private static final Map<UUID, Float> THRESHOLD_WARNED = new HashMap<>();
    private static final Map<UUID, Integer> LOCK_LABEL_COUNTDOWN = new HashMap<>();

    private PrimalstinctPresentation() {
    }

    /** 跨级/锁定变化的表现出口（服务端主线程；与 Service.onChanged 同条件触发）。 */
    public static void onLevelOrLockChanged(ServerPlayerEntity player, int levelBefore, int levelAfter,
                                            boolean lockChanged, boolean lockedNow) {
        if (levelAfter > levelBefore && !lockedNow) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,
                    DARKNESS_TICKS, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,
                    NAUSEA_TICKS, 0, false, false, true));
        }
        if (lockChanged && lockedNow) {
            startLockCinematic(player);
        }
    }

    /** 每 tick 表现检查（Service.tick 内、托管玩家循环中调用）。 */
    public static void tickPlayer(ServerPlayerEntity player) {
        tickLockLabelCountdown(player);
        thresholdApproachCheck(player);
    }

    private static void startLockCinematic(ServerPlayerEntity player) {
        int total = SSCAdapter.transformFxDurationIn() + SSCAdapter.transformFxDurationOut();
        // 移动限制改为缓慢 III（不做完全输入锁），叠加黑暗/恶心增强演出
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,
                LOCK_DARKNESS_TICKS, 0, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,
                LOCK_NAUSEA_TICKS, 0, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                LOCK_SLOWNESS_TICKS, LOCK_SLOWNESS_AMPLIFIER, false, false, true));
        LockCinematicS2C.send(player, total);
        LOCK_LABEL_COUNTDOWN.put(player.getUuid(), total);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 进入满值锁定演出（{} tick）",
                player.getGameProfile().getName(), total);
    }

    /** 锁定演出结束 → 发送锁定 actionbar 文案。 */
    private static void tickLockLabelCountdown(ServerPlayerEntity player) {
        Integer countdown = LOCK_LABEL_COUNTDOWN.get(player.getUuid());
        if (countdown == null) {
            return;
        }
        if (countdown <= 1) {
            LOCK_LABEL_COUNTDOWN.remove(player.getUuid());
            player.sendMessage(Text.translatable("hud.ssc-primalstinct.level_up.locked"), true);
        } else {
            LOCK_LABEL_COUNTDOWN.put(player.getUuid(), countdown - 1);
        }
    }

    private static void thresholdApproachCheck(ServerPlayerEntity player) {
        PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
        float value = component.getValue();
        UUID uuid = player.getUuid();
        if (component.isLocked()) {
            THRESHOLD_WARNED.remove(uuid);
            return;
        }
        float nextThreshold = -1.0f;
        for (float threshold : PrimalRosterManager.active().levels.thresholds) {
            if (value < threshold) {
                nextThreshold = threshold;
                break;
            }
        }
        if (nextThreshold <= 0.0f) {
            THRESHOLD_WARNED.remove(uuid);  // 已无下一阈值（满值前的守态）
            return;
        }
        Float warned = THRESHOLD_WARNED.get(uuid);
        if (value >= nextThreshold - THRESHOLD_APPROACH_WINDOW) {
            if (PrimalstinctService.currentRate(player) > 0.0f && (warned == null || warned != nextThreshold)) {
                player.sendMessage(Text.translatable("chat.ssc-primalstinct.threshold_approach"), false);
                THRESHOLD_WARNED.put(uuid, nextThreshold);
            }
        } else if (warned != null && warned == nextThreshold) {
            THRESHOLD_WARNED.remove(uuid);  // 离开接近窗口，重置（再次接近可再提示）
        }
    }

    public static boolean isLockCinematicActive(ServerPlayerEntity player) {
        return LOCK_LABEL_COUNTDOWN.containsKey(player.getUuid());
    }

    public static void clearPlayer(UUID uuid) {
        THRESHOLD_WARNED.remove(uuid);
        LOCK_LABEL_COUNTDOWN.remove(uuid);
    }
}
