package net.onixary.sscPrimalstinct.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalFormProfile;
import net.onixary.sscPrimalstinct.data.PrimalRoster;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 卡06：形态与等级 Power 的统一挂载结算（服务端主线程）。
 * 触发（合并为 tick 末统一处理）：FORM_CHANGE_END（SSC 已应用子形态与饰品，oldForm==newForm 亦支持）、
 * ACCESSORY_EQUIP/UNEQUIP、登录/复活、数据包 reload 完成、等级跨级（Service 侧）。
 * 差量以 (powerId, sourceId) 为身份：只增删附属来源（form_base / level_N）的差额，
 * 不触碰 origin、子形态、饰品等其他来源——共享同名 power 不误删。
 * 屏蔽 SSC 原能力使用 origin source（getFormLayer().getRight()），
 * 不用附属 source 去删；卸屏蔽不需反向恢复：_loadForm 的 setOrigin 双写会重建合法基线。
 * 禁止照搬 FormUtils.applyPower 的 new Thread+sleep 兜底：仅在注册表就绪的主线程结算，
 * 缺失定义报错跳过（配置校验期已预先拦截大部分）。
 * 注意：附属来源 grant 的 power 不得在 onGrant 产生一次性加值，防止重登刷本能（卡07 约束）。
 */
public final class PrimalPowerReconciler {

    /** 枚举附属 source 的等级上限余量（roster 收缩后清理残留 level_N 用）。 */
    private static final int LEVEL_SOURCE_SCAN_BOUND = 32;

    private static final Set<UUID> PENDING = new LinkedHashSet<>();

    private PrimalPowerReconciler() {
    }

    /** 请求合并结算（任意来源事件调用；END_SERVER_TICK 统一处理）。 */
    public static void requestReconcile(@Nullable ServerPlayerEntity player) {
        if (player != null) {
            PENDING.add(player.getUuid());
        }
    }

    public static void requestReconcileAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PENDING.add(player.getUuid());
        }
    }

    /** END_SERVER_TICK（注册于 PrimalstinctTicker 之后，等级结算先行）。 */
    public static void processPending(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<UUID> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (UUID uuid : batch) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                try {
                    reconcile(player);
                } catch (Throwable t) {
                    SSCPrimalstinct.LOGGER.error("[primalstinct] power reconcile 失败（玩家 {}）",
                            uuid, t);
                }
            }
        }
    }

    public static void reconcile(ServerPlayerEntity player) {
        PrimalRoster roster = PrimalRosterManager.active();
        PowerPlan plan = PowerPlan.EMPTY;
        Identifier formId = null;
        if (SSCAdapter.isLoaded()) {
            formId = SSCAdapter.currentFormIdentifier(player);
            if (formId != null) {
                PrimalFormProfile profile = PrimalRosterManager.resolve(formId);
                int level = RegPrimalstinctComponent.PRIMALSTINCT.get(player).getLevel();
                plan = PowerPlanResolver.resolve(profile, level, roster.levels);
            }
        }

        PowerHolderComponent holder = PowerHolderComponent.KEY.get(player);
        boolean changed = applyAddonGrantDelta(player, holder, plan, roster);
        boolean masked = applyBaselineMasks(player, holder, plan, formId);
        if (changed || masked) {
            PowerHolderComponent.KEY.sync(player);
        }
        // 卡09/10：库存锁槽规则与交互限制规则随 Power 结算更新（规则变化时搬移/暂存/掉落）
        net.onixary.sscPrimalstinct.inventory.InventoryLockManager.updateRule(player);
        net.onixary.sscPrimalstinct.interaction.InteractionRuleManager.updateRule(player);
    }

    /** 附属来源差量：仅对 form_base 与 level_1..N 枚举，不触碰其他来源。 */
    private static boolean applyAddonGrantDelta(ServerPlayerEntity player, PowerHolderComponent holder,
                                                PowerPlan plan, PrimalRoster roster) {
        boolean changed = false;
        int maxLevel = roster.levels.maxLevel();
        // 1) 移除附属来源中不在期望内的 (power, source)
        for (int l = 1; l <= Math.max(maxLevel, LEVEL_SOURCE_SCAN_BOUND); l++) {
            changed |= removeFromSourceIfNotExpected(holder, PowerPlanResolver.levelSource(l), plan);
        }
        changed |= removeFromSourceIfNotExpected(holder, PowerPlanResolver.FORM_BASE_SOURCE, plan);
        // 2) 补齐期望内尚未持有的 (power, source)
        for (Map.Entry<Identifier, Identifier> entry : plan.grants.entrySet()) {
            PowerType<?> type = powerType(entry.getKey(), player);
            if (type == null) {
                continue;
            }
            if (!holder.hasPower(type, entry.getValue())) {
                changed |= holder.addPower(type, entry.getValue());
            }
        }
        return changed;
    }

    private static boolean removeFromSourceIfNotExpected(PowerHolderComponent holder,
                                                         Identifier source, PowerPlan plan) {
        boolean changed = false;
        for (PowerType<?> type : List.copyOf(holder.getPowersFromSource(source))) {
            if (!source.equals(plan.grants.get(type.getIdentifier()))) {
                holder.removePower(type, source);  // apoli removePower 返回 void
                changed = true;
            }
        }
        return changed;
    }

    /** 屏蔽 SSC origin 基线：以 origin source 删除；不盲加回（_loadForm 已重建基线）。 */
    private static boolean applyBaselineMasks(ServerPlayerEntity player, PowerHolderComponent holder,
                                              PowerPlan plan, @Nullable Identifier formId) {
        if (plan.maskedBaseline.isEmpty() || formId == null) {
            return false;
        }
        Identifier originSource = SSCAdapter.formLayerSource(formId);
        if (originSource == null) {
            return false;
        }
        boolean changed = false;
        for (Identifier masked : plan.maskedBaseline) {
            PowerType<?> type = powerType(masked, player);
            if (type == null) {
                continue;
            }
            if (holder.hasPower(type, originSource)) {
                holder.removePower(type, originSource);
                changed = true;
            }
        }
        return changed;
    }

    private static @Nullable PowerType<?> powerType(Identifier powerId, ServerPlayerEntity player) {
        if (!PowerTypeRegistry.contains(powerId)) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] power {} 未注册，跳过结算（玩家 {}）——请检查配置引用",
                    powerId, player.getGameProfile().getName());
            return null;
        }
        return PowerTypeRegistry.get(powerId);
    }
}
