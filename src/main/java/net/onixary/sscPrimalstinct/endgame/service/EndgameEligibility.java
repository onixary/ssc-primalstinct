package net.onixary.sscPrimalstinct.endgame.service;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRosterManager;
import net.onixary.sscPrimalstinct.endgame.data.EndgameVariantMapping;
import net.onixary.sscPrimalstinct.endgame.state.RegEndgameComponent;
import org.jetbrains.annotations.Nullable;

/**
 * 眷属实现02：终局资格判定的唯一入口（服务端实际状态）。
 * 三个独立判定：献祭（供物投入基座）、领取（右键祭坛取碎片）、转化（转化台蹲下变形）。
 * 入口条件读取服务端实际等级（PrimalstinctComponent 派生），锁定 flag 不作为额外门槛；
 * 一项需求只有一个服务端规则入口，客户端不自行决定资格。
 */
public final class EndgameEligibility {

    private EndgameEligibility() {
    }

    /** 当前玩家形态是否在本能名单内（新本能系统接管的形态）。 */
    public static boolean inRosterForm(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        Identifier formId = SSCAdapter.currentFormIdentifier(player);
        return formId != null && PrimalRosterManager.resolve(formId) != null;
    }

    /** 是否达到当前等级表的最高级（读取 maxLevel，不写死）。 */
    public static boolean atMaxLevel(@Nullable ServerPlayerEntity player) {
        if (!inRosterForm(player)) {
            return false;
        }
        int level = RegPrimalstinctComponent.PRIMALSTINCT.get(player).getLevel();
        return level >= EndgameRules.maxInstinctLevel();
    }

    /** 献祭资格：名单内形态 + 实际最高级（投掷者在消费时再核验一次，眷属实现05）。 */
    public static boolean canSacrifice(@Nullable ServerPlayerEntity player) {
        return atMaxLevel(player);
    }

    /** 领取资格：名单内形态 + 实际最高级（祭坛 ACTIVE/实例未领由 RitualService 在领取点核验）。 */
    public static boolean canClaimReward(@Nullable ServerPlayerEntity player) {
        return atMaxLevel(player);
    }

    /** 转化台变形资格：名单内 + 最高级 + 映射有效 + source 非变体 + 无运行会话。 */
    public static TransformCheck canTransform(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return TransformCheck.fail("not_player");
        }
        if (!inRosterForm(player)) {
            return TransformCheck.fail("not_roster_form");
        }
        if (!atMaxLevel(player)) {
            return TransformCheck.fail("not_max_level");
        }
        if (RegEndgameComponent.ENDGAME.get(player).hasRunningSession()) {
            return TransformCheck.fail("session_running");
        }
        Identifier sourceForm = SSCAdapter.currentFormIdentifier(player);
        if (sourceForm == null) {
            return TransformCheck.fail("no_form");
        }
        // 已是目标变体：无操作（幂等），不是失败
        if (EndgameRosterManager.isVariant(sourceForm)) {
            return new TransformCheck(false, true, null, null);
        }
        EndgameVariantMapping mapping = EndgameRosterManager.variantFor(sourceForm);
        if (mapping == null) {
            return TransformCheck.fail("no_mapping");
        }
        return TransformCheck.ok(mapping);
    }

    /** 判定结果：ok=可变形；alreadyApplied=目标已生效（无操作）；reason=失败原因键。 */
    public record TransformCheck(boolean ok, boolean alreadyApplied,
                                 @Nullable String reason, @Nullable EndgameVariantMapping mapping) {
        static TransformCheck ok(EndgameVariantMapping mapping) {
            return new TransformCheck(true, false, null, mapping);
        }

        static TransformCheck fail(String reason) {
            return new TransformCheck(false, false, reason, null);
        }
    }
}
