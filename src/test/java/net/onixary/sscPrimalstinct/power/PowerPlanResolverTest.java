package net.onixary.sscPrimalstinct.power;

import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.data.PrimalFormProfile;
import net.onixary.sscPrimalstinct.data.PrimalLevels;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 卡06：期望能力集合计算的稳定性——跨级往返、重复计算、后级撤销前级、base 优先级。
 */
public class PowerPlanResolverTest {

    private static final PrimalLevels LEVELS = buildLevels();

    private static PrimalLevels buildLevels() {
        PrimalLevels.Builder builder = new PrimalLevels.Builder(100.0f, true,
                new float[]{20.0f, 40.0f, 60.0f, 80.0f, 100.0f});
        builder.level(1, Set.of(id("l1_power")), Set.<Identifier>of());
        builder.level(2, Set.of(id("l2_power")), Set.<Identifier>of());
        builder.level(3, Set.<Identifier>of(), Set.of(id("l1_power")));  // L3 撤销 l1_power（后级撤销前级）
        builder.level(4, Set.of(id("l1_power")), Set.<Identifier>of());  // L4 重新引入 → source 应更新为 level_4
        builder.level(5);
        return builder.build();
    }

    private static Identifier id(String path) {
        return Identifier.of("test", path);
    }

    private static PrimalFormProfile profile(Map<Integer, PrimalLevels.LevelPowers> overrides) {
        return new PrimalFormProfile(
                id("form"), true, 1, null,
                List.of(id("base_power")), List.of(id("origin_power_to_mask")),
                overrides, List.of(), id("diet"), id("sleep"), "test.json");
    }

    @Test
    public void level0OnlyBasePowers() {
        PowerPlan plan = PowerPlanResolver.resolve(profile(Map.of()), 0, LEVELS);
        assertEquals(Map.of(id("base_power"), PowerPlanResolver.FORM_BASE_SOURCE), plan.grants);
        assertEquals(Set.of(id("origin_power_to_mask")), plan.maskedBaseline);
    }

    @Test
    public void cumulativeExpansionAndRevocation() {
        // L2：base + l1 + l2
        PowerPlan plan = PowerPlanResolver.resolve(profile(Map.of()), 2, LEVELS);
        assertEquals(PowerPlanResolver.levelSource(1), plan.grants.get(id("l1_power")));
        assertEquals(PowerPlanResolver.levelSource(2), plan.grants.get(id("l2_power")));
        assertEquals(PowerPlanResolver.FORM_BASE_SOURCE, plan.grants.get(id("base_power")));
        // L3：l1_power 被撤销
        plan = PowerPlanResolver.resolve(profile(Map.of()), 3, LEVELS);
        assertFalse(plan.grants.containsKey(id("l1_power")));
        assertTrue(plan.grants.containsKey(id("l2_power")));
        // L4：重新引入 → source 更新为 level_4
        plan = PowerPlanResolver.resolve(profile(Map.of()), 4, LEVELS);
        assertEquals(PowerPlanResolver.levelSource(4), plan.grants.get(id("l1_power")));
    }

    @Test
    public void stableAcrossRepeatedResolve() {
        // 跨级往返与重复计算得到稳定终态（不叠属性）
        PowerPlan up = PowerPlanResolver.resolve(profile(Map.of()), 5, LEVELS);
        PowerPlan down = PowerPlanResolver.resolve(profile(Map.of()), 1, LEVELS);
        PowerPlan upAgain = PowerPlanResolver.resolve(profile(Map.of()), 5, LEVELS);
        assertEquals(up.grants, upAgain.grants);
        assertTrue(down.grants.containsKey(id("l1_power")));
        assertFalse(down.grants.containsKey(id("l2_power")));
    }

    @Test
    public void levelOverridesStackAfterCommon() {
        // form 级覆盖：L2 覆盖撤销 l2_power、追加 override_power
        Map<Integer, PrimalLevels.LevelPowers> overrides = new HashMap<>();
        overrides.put(2, new PrimalLevels.LevelPowers(
                Set.of(id("override_power")), Set.of(id("l2_power"))));
        PowerPlan plan = PowerPlanResolver.resolve(profile(overrides), 2, LEVELS);
        assertFalse(plan.grants.containsKey(id("l2_power")));
        assertEquals(PowerPlanResolver.levelSource(2), plan.grants.get(id("override_power")));
    }

    @Test
    public void nullProfileYieldsEmpty() {
        assertEquals(PowerPlan.EMPTY, PowerPlanResolver.resolve(null, 3, LEVELS));
    }
}
