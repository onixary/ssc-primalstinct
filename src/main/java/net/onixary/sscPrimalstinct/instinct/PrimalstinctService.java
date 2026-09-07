package net.onixary.sscPrimalstinct.instinct;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 卡05：本能数值、等级与满值锁定内核（服务端主线程事务）。
 * 统一入口：modify（即时事件与 tick 合并变化都走这里）、setRate/removeRate（稳定键速率贡献）。
 * tick 顺序：确保基础增长→采样合并速率→应用持续变化→边界（跨级/锁定）→（卡06 能力差量钩子）→同步。
 * 速率统一“点/秒”，tick 时除以 20；基础自然增长也是 POWER 来源，满值即停。
 * 运行期速率不落盘：断线清理，重登重算（基础增长由 tick 按名单自动重建）。
 */
public final class PrimalstinctService {

    /** 开发初值：空条到满 150 分钟（对齐旧 SSC 9000 秒节奏；卡18 试玩后调整）。 */
    public static final float BASE_GROWTH_PER_SECOND = 100.0f / 9000.0f;
    public static final String BASE_RATE_KEY = "primalstinct:base";

    /** 每玩家运行期速率贡献（稳定键 → 贡献）。 */
    private static final Map<UUID, LinkedHashMap<String, PrimalstinctKernel.Contribution>> RATE_STATES = new LinkedHashMap<>();

    private PrimalstinctService() {
    }

    public static PrimalstinctComponent component(ServerPlayerEntity player) {
        return RegPrimalstinctComponent.PRIMALSTINCT.get(player);
    }

    /** 统一即时修改入口；返回是否被接受。 */
    public static boolean modify(ServerPlayerEntity player, PrimalstinctSource source, float delta) {
        if (!PrimalstinctLifecycle.isManaged(player) && source != PrimalstinctSource.ADMIN) return false;
        PrimalstinctComponent component = component(player);
        component.initializeInstinct();
        float maxValue = PrimalRosterManager.active().levels.maxValue;
        int levelBefore = component.getLevel();
        PrimalstinctKernel.ModifyResult result = PrimalstinctKernel.modify(
                component.getValue(), component.isLocked(), source, delta, maxValue);
        if (!result.applied()) {
            return false;
        }
        component.setValue(result.value());
        component.setLocked(result.locked());
        boolean levelCrossed = component.getLevel() != levelBefore;
        if (levelCrossed || result.lockChanged()) {
            onChanged(player);
            PrimalstinctPresentation.onLevelOrLockChanged(player, levelBefore, component.getLevel(),
                    result.lockChanged(), component.isLocked());
        }
        return true;
    }

    /** 跨级/锁定变化的统一出口：同步快照 + 请求 Power 差量结算（卡06）。 */
    private static void onChanged(ServerPlayerEntity player) {
        PrimalstinctNetwork.syncNow(player);
        net.onixary.sscPrimalstinct.power.PrimalPowerReconciler.requestReconcile(player);
    }

    /** 注册/覆盖一个稳定键速率贡献（点/秒）。 */
    public static void setRate(ServerPlayerEntity player, String key, PrimalstinctSource source,
                               float pointsPerSecond) {
        if (!PrimalstinctLifecycle.isManaged(player)) return;
        if (!Float.isFinite(pointsPerSecond)) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 拒绝非有限速率 {}（key={}）", pointsPerSecond, key);
            return;
        }
        rateState(player).put(key, new PrimalstinctKernel.Contribution(key, source, pointsPerSecond));
    }

    /** 卸载某稳定键的速率贡献（条件失效/Power 卸载时立即调用）。 */
    public static void removeRate(ServerPlayerEntity player, String key) {
        rateState(player).remove(key);
    }

    public static @Nullable PrimalstinctKernel.Contribution getRate(ServerPlayerEntity player, String key) {
        return rateState(player).get(key);
    }

    public static List<PrimalstinctKernel.Contribution> listRates(ServerPlayerEntity player) {
        return new ArrayList<>(rateState(player).values());
    }

    /** 当前合并速率（点/秒），供 S2C 快照与客户端外推。 */
    public static float currentRate(ServerPlayerEntity player) {
        if (!PrimalstinctLifecycle.isManaged(player)) return 0.0f;
        PrimalstinctComponent component = component(player);
        return PrimalstinctKernel.mergeRate(
                component.getValue(), component.isLocked(), rateState(player).values());
    }

    /** 断线清理运行期速率与表现状态（重登后由 tick/Power 重建）。 */
    public static void clearPlayer(UUID uuid) {
        RATE_STATES.remove(uuid);
        PrimalstinctPresentation.clearPlayer(uuid);
        WanderAiController.clearPlayer(uuid);
    }

    /** 每 tick 主循环（END_SERVER_TICK，主线程）。 */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PrimalstinctLifecycle.refresh(player);
            if (!PrimalstinctLifecycle.isManaged(player)) continue;
            // 1) 基础自然增长：名单内形态自动持有（POWER 来源，满值即停）
            ensureBaseRate(player);
            // 1.5) 速率 Power 扫描：活跃贡献/失活移除（卡07，稳定键 "power:<source_id>"）
            scanRatePowers(player);
            // 2) 采样合并持续变化并应用（分来源事务：外部负向不被 POWER 拒改误伤）
            PrimalstinctComponent component = component(player);
            int levelBefore = component.getLevel();
            boolean lockChanged = false;
            Map<PrimalstinctSource, Float> perSecond =
                    PrimalstinctKernel.mergeBySource(component.getValue(), component.isLocked(), rateState(player).values());
            if (!perSecond.isEmpty()) {
                Map<PrimalstinctSource, Float> perTick = new java.util.EnumMap<>(PrimalstinctSource.class);
                perSecond.forEach((source, amount) -> perTick.put(source, PrimalstinctKernel.perTick(amount)));
                float maxValue = PrimalRosterManager.active().levels.maxValue;
                PrimalstinctKernel.ModifyResult result = PrimalstinctKernel.modifyMulti(
                        component.getValue(), component.isLocked(), perTick, maxValue);
                if (result.applied()) {
                    component.setValue(result.value());
                    component.setLocked(result.locked());
                    lockChanged = result.lockChanged();
                }
            }
            // 3) 边界处理：跨级/锁定变化 → 同步 + Power 结算（与命令路径同一出口）+ 表现编排
            if (component.getLevel() != levelBefore || lockChanged) {
                onChanged(player);
                PrimalstinctPresentation.onLevelOrLockChanged(player, levelBefore, component.getLevel(),
                        lockChanged, component.isLocked());
            }
            // 3.5) 蜷缩校验（位置偏离/死亡/离地面自动退出，卡11）
            net.onixary.sscPrimalstinct.sleep.CurlSleepController.validate(player);
            // 4) 表现检查（阈值前提示 / 锁定标签倒计时）
            PrimalstinctPresentation.tickPlayer(player);
            // 5) 游荡 AI 接管检查（instinct_wander_ai Power）
            WanderAiController.tickPlayer(player);
        }
        RATE_STATES.keySet().removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
    }

    private static void ensureBaseRate(ServerPlayerEntity player) {
        LinkedHashMap<String, PrimalstinctKernel.Contribution> state = rateState(player);
        boolean inRoster = false;
        if (SSCAdapter.isLoaded()) {
            String formId = SSCAdapter.readInstinct(player).formId();
            inRoster = formId != null && PrimalRosterManager.resolve(net.minecraft.util.Identifier.tryParse(formId)) != null;
        }
        if (inRoster) {
            state.putIfAbsent(BASE_RATE_KEY, new PrimalstinctKernel.Contribution(
                    BASE_RATE_KEY, PrimalstinctSource.POWER, BASE_GROWTH_PER_SECOND));
        } else {
            state.remove(BASE_RATE_KEY);
        }
    }

    /**
     * 卡07：modify_primalstinct_rate Power 扫描——活跃时贡献、失活/撤销移除；
     * 重挂载不产生一次性改值（无"获得即加值"路径）。
     */
    private static void scanRatePowers(ServerPlayerEntity player) {
        java.util.Set<String> activeKeys = new java.util.HashSet<>();
        for (net.onixary.sscPrimalstinct.power.factory.ModifyPrimalstinctRatePower power :
                io.github.apace100.apoli.component.PowerHolderComponent.getPowers(player, net.onixary.sscPrimalstinct.power.factory.ModifyPrimalstinctRatePower.class)) {
            if (power.isActive()) {
                activeKeys.add(power.contributionKey());
                setRate(player, power.contributionKey(), PrimalstinctSource.POWER, power.getRatePerSecond());
            }
        }
        for (String key : new java.util.HashSet<>(rateState(player).keySet())) {
            if (key.startsWith(net.onixary.sscPrimalstinct.power.factory.ModifyPrimalstinctRatePower.CONTRIBUTION_KEY_PREFIX)
                    && !activeKeys.contains(key)) {
                removeRate(player, key);
            }
        }
    }

    private static LinkedHashMap<String, PrimalstinctKernel.Contribution> rateState(ServerPlayerEntity player) {
        return RATE_STATES.computeIfAbsent(player.getUuid(), uuid -> new LinkedHashMap<>());
    }
}
