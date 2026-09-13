package net.onixary.sscPrimalstinct.endgame.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.endgame.data.EndgameVariantMapping;
import net.onixary.sscPrimalstinct.endgame.state.EndgamePlayerComponent;
import net.onixary.sscPrimalstinct.endgame.state.RegEndgameComponent;
import net.onixary.sscPrimalstinct.endgame.state.TransformPhase;
import net.onixary.sscPrimalstinct.instinct.WanderAiController;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;
import net.onixary.sscPrimalstinct.power.PrimalPowerReconciler;
import net.onixary.sscPrimalstinct.sleep.CurlSleepController;

import java.util.UUID;

/**
 * 眷属实现13（A 阶段最小版）：转化会话状态机与完成结算。
 * 会话语义“启动后提交”：资格在启动时判定一次，演出中不因松开蹲键取消；
 * 死亡/断线/其它形态变更走恢复或终止。
 * 完成以实际 FormID 生效为准（FORM_CHANGE_END / 登录对账），SSC 回调与事件可能重复到达，
 * 完成事务以 phase==STARTED 为一次性守卫，幂等。
 */
public final class TransformationService {

    private TransformationService() {
    }

    /** 转化台检测入口（PrimalConversionPlatformBlockEntity 每 N tick 调用）。 */
    public static void tryStartFromPlatform(ServerPlayerEntity player, BlockPos platformPos) {
        // 下蹲是转化台的显式触发条件（设计稿“原初转化台”）
        if (!player.isSneaking()) {
            return;
        }
        EndgameEligibility.TransformCheck check = EndgameEligibility.canTransform(player);
        if (check.alreadyApplied()) {
            return;  // 已是变体：无操作
        }
        if (!check.ok()) {
            // 任一条件不满足均无效（不弹窗刷屏；失败原因进 debug 日志）
            SSCPrimalstinct.LOGGER.debug("[primalstinct] 转化资格未通过（{}，玩家 {}）",
                    check.reason(), player.getGameProfile().getName());
            return;
        }
        startSession(player, check.mapping(), platformPos);
    }

    private static void startSession(ServerPlayerEntity player, EndgameVariantMapping mapping, BlockPos platformPos) {
        EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
        UUID sessionId = UUID.randomUUID();
        // 先落持久状态，再启动 SSC 变形（眷属实现13：首次满足条件即建立会话并持久化）
        component.startSession(sessionId, mapping.sourceForm, mapping.targetForm,
                player.getWorld().getRegistryKey().getValue().toString(), platformPos);

        // 演出开始前停止游荡 AI 与蜷睡等主动控制，避免被自身控制器拉离
        WanderAiController.stopPlayer(player);
        CurlSleepController.wakeUp(player, "endgame_transform");

        // 启动同 tick 触发化身 Interact（无化身/异维度静默跳过，不阻断——眷属实现11/13）；
        // 变形本体延迟 1 秒启动，让化身动画先起势（用户决策 2026-09-12）
        net.onixary.sscPrimalstinct.endgame.entity.PrimalAvatarEntity.tryPlayInteract(player, sessionId, platformPos);
        PENDING_LAUNCH.put(player.getUuid(), new PendingLaunch(sessionId, mapping.targetForm,
                player.getWorld().getServer().getTicks() + TRANSFORM_DELAY_TICKS));
        SSCPrimalstinct.LOGGER.info("[primalstinct] 转化会话启动：{} → {}（session={}，玩家 {}，{}t 后变形）",
                mapping.sourceForm, mapping.targetForm, sessionId, player.getGameProfile().getName(),
                TRANSFORM_DELAY_TICKS);
    }

    /** 变形启动延迟（tick）：蹲台触发后先播化身动画，再进入 SSC 变形演出。 */
    private static final int TRANSFORM_DELAY_TICKS = 20;
    /** 待启动延迟变形（玩家 → 会话）；断线/会话变化时作废。 */
    private record PendingLaunch(UUID sessionId, net.minecraft.util.Identifier targetForm, int fireTick) {
    }

    private static final java.util.Map<UUID, PendingLaunch> PENDING_LAUNCH = new java.util.HashMap<>();

    /** 主入口注册：延迟变形派发（END_SERVER_TICK）。 */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TransformationService::processPendingLaunches);
    }

    private static void processPendingLaunches(net.minecraft.server.MinecraftServer server) {
        if (PENDING_LAUNCH.isEmpty()) {
            return;
        }
        var iterator = PENDING_LAUNCH.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            PendingLaunch pending = entry.getValue();
            if (player == null) {
                iterator.remove();  // 断线作废：登录对账按"仍为源形态"清理会话
                continue;
            }
            if (server.getTicks() < pending.fireTick()) {
                continue;
            }
            iterator.remove();
            // 会话仍在同一启动中状态才派发（期间被终止则静默放弃）
            EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
            if (component.getTransformPhase() != TransformPhase.STARTED
                    || !pending.sessionId().equals(component.getTransformSessionId())) {
                continue;
            }
            boolean started = SSCAdapter.startPrimalTransformation(player, pending.targetForm(),
                    () -> onTargetFormApplied(player));
            if (!started) {
                component.setPhase(TransformPhase.FAILED);
                component.clearSession();
                SSCPrimalstinct.LOGGER.warn("[primalstinct] 变形启动失败（target={}，玩家 {}）",
                        pending.targetForm(), player.getGameProfile().getName());
            }
        }
    }

    /**
     * 完成事务：目标 FormID 实际生效后执行一次（phase==STARTED 守卫，重复到达幂等）。
     * 结算顺序：标记 FORM_APPLIED → 能力 reconcile（背包/阅读随 Power 集自动恢复）→
     * 同步快照 → 结束表现 → 形态专属 Info → COMPLETED 并清理会话。
     */
    public static void onTargetFormApplied(ServerPlayerEntity player) {
        EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
        if (component.getTransformPhase() != TransformPhase.STARTED) {
            return;  // 重复回调/事件守卫
        }
        Identifier target = component.getTransformTarget();
        if (target == null) {
            component.clearSession();
            return;
        }
        component.setPhase(TransformPhase.FORM_APPLIED);
        // 能力结算与背包锁恢复：reconcile 依 PowerPlan 差量与 InventoryLockManager.updateRule 完成；
        // FORM_CHANGE_END 钩子也会请求一次，此处显式请求保证时序（幂等）
        PrimalPowerReconciler.requestReconcile(player);
        PrimalstinctNetwork.syncNow(player);
        // 结束表现
        player.getWorld().playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 0.8f);
        // 形态专属完成信息：发送到原版聊天框（无映射时不发）
        EndgameVariantMapping mapping = net.onixary.sscPrimalstinct.endgame.data.EndgameRosterManager.variantFor(
                component.getTransformSource() == null ? target : component.getTransformSource());
        if (mapping != null) {
            player.sendMessage(Text.translatable(mapping.completionInfoKey).formatted(Formatting.RED), false);
        }
        component.setPhase(TransformPhase.COMPLETED);
        component.clearSession();
        SSCPrimalstinct.LOGGER.info("[primalstinct] 转化完成：玩家 {} 已变为 {}",
                player.getGameProfile().getName(), target);
    }

    /**
     * 登录对账（SSC onTransformComplete 回调退出重进失效的兜底）：
     * 目标已生效 → 补完成事务；仍为源形态 → 清会话等待重新蹲；
     * 其它形态 → 终止，不强行覆盖玩家新状态。
     */
    public static void reconcileOnJoin(ServerPlayerEntity player) {
        EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
        if (!component.hasRunningSession()) {
            return;
        }
        Identifier current = SSCAdapter.currentFormIdentifier(player);
        Identifier target = component.getTransformTarget();
        Identifier source = component.getTransformSource();
        if (target != null && target.equals(current)) {
            onTargetFormApplied(player);
        } else if (source != null && source.equals(current)) {
            component.setPhase(TransformPhase.FAILED);
            component.clearSession();
            SSCPrimalstinct.LOGGER.info("[primalstinct] 登录对账：玩家 {} 仍为源形态，清理未完成会话",
                    player.getGameProfile().getName());
        } else {
            component.setPhase(TransformPhase.FAILED);
            component.clearSession();
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 登录对账：玩家 {} 处于其它形态 {}，终止旧转化会话",
                    player.getGameProfile().getName(), current);
        }
    }

    /** FORM_CHANGE_END 钩子：实际 FormID 生效即结算（服务端）。 */
    public static void onFormChangeEnd(net.minecraft.entity.player.PlayerEntity player,
                                       Identifier oldForm, Identifier newForm) {
        if (player.getWorld().isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(serverPlayer);
        if (component.hasRunningSession() && newForm != null && newForm.equals(component.getTransformTarget())) {
            onTargetFormApplied(serverPlayer);
        }
    }
}
