package net.onixary.sscPrimalstinct.power;

import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.data.PrimalFormProfile;
import net.onixary.sscPrimalstinct.data.PrimalLevels;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 卡06：期望能力集合的唯一计算点（纯函数，可单测）。
 * 展开顺序：profile.base_powers.add → L1..当前级（每级先公共表增量、后 form 覆盖增量，
 * remove 先行、add 记录引入级）；power 的授予 source = 其最终引入等级 level_N，
 * base 来源优先——跨级往返/重复计算得到稳定终态，不重复叠属性。
 */
public final class PowerPlanResolver {

    public static final Identifier FORM_BASE_SOURCE = Identifier.of(SSCPrimalstinct.MOD_ID, "form_base");

    private PowerPlanResolver() {
    }

    public static Identifier levelSource(int level) {
        return Identifier.of(SSCPrimalstinct.MOD_ID, "level_" + level);
    }

    public static PowerPlan resolve(@Nullable PrimalFormProfile profile, int level, PrimalLevels levels) {
        if (profile == null) {
            return PowerPlan.EMPTY;
        }
        Map<Identifier, Identifier> grants = new HashMap<>();
        Set<Identifier> masked = new HashSet<>(profile.basePowersRemove);

        for (Identifier power : profile.basePowersAdd) {
            grants.put(power, FORM_BASE_SOURCE);
        }

        Set<Identifier> running = new HashSet<>();
        Map<Identifier, Integer> introduction = new HashMap<>();
        int target = Math.max(0, Math.min(level, levels.maxLevel()));
        for (int l = 1; l <= target; l++) {
            PrimalLevels.LevelPowers common = levels.incrementsFor(l);
            applyIncrement(running, introduction, common.add, common.remove, l);
            PrimalLevels.LevelPowers override = profile.levelOverrides.get(l);
            if (override != null) {
                applyIncrement(running, introduction, override.add, override.remove, l);
            }
        }
        // 只授予最终仍在生效集合中的 power（引入后被撤销的不授予；source 为其最终引入级）
        for (Map.Entry<Identifier, Integer> entry : introduction.entrySet()) {
            if (running.contains(entry.getKey())) {
                grants.putIfAbsent(entry.getKey(), levelSource(entry.getValue()));
            }
        }
        return new PowerPlan(grants, masked);
    }

    /** 等级增量应用：remove 先行（后级可显式撤销前级能力），add 记录（最终）引入级。 */
    private static void applyIncrement(Set<Identifier> running, Map<Identifier, Integer> introduction,
                                       Set<Identifier> add, Set<Identifier> remove, int level) {
        for (Identifier power : remove) {
            running.remove(power);
        }
        for (Identifier power : add) {
            if (running.add(power)) {
                introduction.put(power, level);
            } else {
                introduction.put(power, level);  // 移除后重新引入：source 更新为最新引入级
            }
        }
    }
}
