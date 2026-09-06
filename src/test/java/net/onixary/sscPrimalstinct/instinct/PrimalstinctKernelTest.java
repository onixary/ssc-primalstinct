package net.onixary.sscPrimalstinct.instinct;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 卡05 验收的纯函数部分：边界跨级、满值锁定、合法解锁、越界/NaN、多来源合并、
 * 锁定拒绝 POWER（含 Power Action 自解锁尝试）。
 */
public class PrimalstinctKernelTest {

    private static final float MAX = 100.0f;
    private static final float EPS = 1e-6f;

    @Test
    public void boundaryLevelCross() {
        // 19.99 → 20（跨 L1 阈值）
        PrimalstinctKernel.ModifyResult r = PrimalstinctKernel.modify(19.99f, false, PrimalstinctSource.ADMIN, 0.01f, MAX);
        assertTrue(r.applied());
        assertEquals(20.0f, r.value(), EPS);
        assertFalse(r.locked());
        // 99.99 → 100（满值锁定）
        r = PrimalstinctKernel.modify(99.99f, false, PrimalstinctSource.ADMIN, 0.01f, MAX);
        assertTrue(r.applied());
        assertEquals(100.0f, r.value(), EPS);
        assertTrue(r.locked());
        assertTrue(r.lockChanged());
    }

    @Test
    public void lockRejectsPowerBothSigns() {
        // 满值锁定后 POWER 正负皆拒（Power Action 不能自解锁）
        assertFalse(PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.POWER, -1.0f, MAX).applied());
        assertFalse(PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.POWER, 1.0f, MAX).applied());
        // 状态不变
        PrimalstinctKernel.ModifyResult r = PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.POWER, -5.0f, MAX);
        assertEquals(100.0f, r.value(), EPS);
        assertTrue(r.locked());
    }

    @Test
    public void externalNegativeUnlocks() {
        // 100 → 99：外部合法负修改立即解除
        PrimalstinctKernel.ModifyResult r = PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.ITEM_RECOVERY, -1.0f, MAX);
        assertTrue(r.applied());
        assertEquals(99.0f, r.value(), EPS);
        assertFalse(r.locked());
        assertTrue(r.lockChanged());
        // WORLD_RECOVERY 与 ADMIN 同样可解除
        assertTrue(PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.WORLD_RECOVERY, -0.5f, MAX).applied());
        assertTrue(PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.ADMIN, -10.0f, MAX).applied());
    }

    @Test
    public void positiveAtMaxIsNoOp() {
        // 满值处的正向外部修改：无处可加，保持锁定
        PrimalstinctKernel.ModifyResult r = PrimalstinctKernel.modify(100.0f, true, PrimalstinctSource.ITEM_RECOVERY, 5.0f, MAX);
        assertFalse(r.applied());
        assertTrue(r.locked());
    }

    @Test
    public void clampOutOfRange() {
        // 越界与负向越界
        assertEquals(100.0f, PrimalstinctKernel.modify(95.0f, false, PrimalstinctSource.ADMIN, 500.0f, MAX).value(), EPS);
        assertEquals(0.0f, PrimalstinctKernel.modify(3.0f, false, PrimalstinctSource.ADMIN, -500.0f, MAX).value(), EPS);
        assertTrue(PrimalstinctKernel.modify(95.0f, false, PrimalstinctSource.ADMIN, 500.0f, MAX).locked());
    }

    @Test
    public void nanIsRejectedAndNeverPropagates() {
        assertFalse(PrimalstinctKernel.modify(50.0f, false, PrimalstinctSource.ADMIN, Float.NaN, MAX).applied());
        assertEquals(50.0f, PrimalstinctKernel.modify(50.0f, false, PrimalstinctSource.ADMIN, Float.NaN, MAX).value(), EPS);
        // 无穷大同样拒绝
        assertFalse(PrimalstinctKernel.modify(50.0f, false, PrimalstinctSource.ADMIN, Float.POSITIVE_INFINITY, MAX).applied());
        // 脏输入值：钳制回合法域而非传播
        assertEquals(0.0f, PrimalstinctKernel.modify(Float.NaN, false, PrimalstinctSource.ADMIN, 1.0f, MAX).value(), EPS);
    }

    @Test
    public void multipleSourcesMergeWithDedup() {
        // 多个正负来源按稳定键合并
        float rate = PrimalstinctKernel.mergeRate(50.0f, false, List.of(
                new PrimalstinctKernel.Contribution("a", PrimalstinctSource.POWER, 0.5f),
                new PrimalstinctKernel.Contribution("b", PrimalstinctSource.POWER, -0.2f),
                new PrimalstinctKernel.Contribution("c", PrimalstinctSource.ITEM_RECOVERY, 0.1f)));
        assertEquals(0.4f, rate, EPS);
        // 非有限速率贡献被忽略
        rate = PrimalstinctKernel.mergeRate(50.0f, false, List.of(
                new PrimalstinctKernel.Contribution("a", PrimalstinctSource.POWER, 0.5f),
                new PrimalstinctKernel.Contribution("bad", PrimalstinctSource.POWER, Float.NaN)));
        assertEquals(0.5f, rate, EPS);
    }

    @Test
    public void lockedStopsPowerGrowthButAllowsNegativeExternal() {
        // 锁定时：POWER 增长停止，正向外源忽略，仅负向外源参与合并（解锁路径）
        float rate = PrimalstinctKernel.mergeRate(100.0f, true, List.of(
                new PrimalstinctKernel.Contribution(PrimalstinctService.BASE_RATE_KEY, PrimalstinctSource.POWER, 0.0111f),
                new PrimalstinctKernel.Contribution("buff", PrimalstinctSource.POWER, 5.0f),
                new PrimalstinctKernel.Contribution("recover", PrimalstinctSource.ITEM_RECOVERY, -0.5f),
                new PrimalstinctKernel.Contribution("gain", PrimalstinctSource.ITEM_RECOVERY, 1.0f)));
        assertEquals(-0.5f, rate, EPS);
    }

    @Test
    public void perTickConversion() {
        // 点/秒 → 每 tick：除以 20
        assertEquals(0.05f, PrimalstinctKernel.perTick(1.0f), EPS);
        assertEquals(PrimalstinctService.BASE_GROWTH_PER_SECOND / 20.0f,
                PrimalstinctKernel.perTick(PrimalstinctService.BASE_GROWTH_PER_SECOND), EPS);
    }

    @Test
    public void modifyMultiRoutesBySource() {
        // 卡05 实测回归：锁定时负向外源速率必须解锁（不能被“拒 POWER”误伤）
        java.util.Map<PrimalstinctSource, Float> deltas = new java.util.EnumMap<>(PrimalstinctSource.class);
        deltas.put(PrimalstinctSource.ITEM_RECOVERY, -1.0f);
        PrimalstinctKernel.ModifyResult r = PrimalstinctKernel.modifyMulti(100.0f, true, deltas, MAX);
        assertTrue(r.applied());
        assertEquals(99.0f, r.value(), EPS);
        assertFalse(r.locked());

        // 锁定时 POWER 部分整段丢弃，仅外部生效
        deltas = new java.util.EnumMap<>(PrimalstinctSource.class);
        deltas.put(PrimalstinctSource.POWER, 5.0f);
        deltas.put(PrimalstinctSource.WORLD_RECOVERY, -2.0f);
        r = PrimalstinctKernel.modifyMulti(100.0f, true, deltas, MAX);
        assertTrue(r.applied());
        assertEquals(98.0f, r.value(), EPS);
        assertFalse(r.locked());

        // 未锁定：混合来源合并应用
        deltas = new java.util.EnumMap<>(PrimalstinctSource.class);
        deltas.put(PrimalstinctSource.POWER, 0.5f);
        deltas.put(PrimalstinctSource.ADMIN, -0.2f);
        r = PrimalstinctKernel.modifyMulti(50.0f, false, deltas, MAX);
        assertTrue(r.applied());
        assertEquals(50.3f, r.value(), EPS);

        // 锁定且满值处：POWER+外部正向全为 no-op
        deltas = new java.util.EnumMap<>(PrimalstinctSource.class);
        deltas.put(PrimalstinctSource.POWER, 5.0f);
        deltas.put(PrimalstinctSource.ITEM_RECOVERY, 3.0f);
        r = PrimalstinctKernel.modifyMulti(100.0f, true, deltas, MAX);
        assertFalse(r.applied());
        assertTrue(r.locked());
    }
}
