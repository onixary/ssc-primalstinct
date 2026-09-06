package net.onixary.sscPrimalstinct.instinct;

/**
 * 卡05：本能数值纯函数内核（不依赖 Minecraft 类，可单元测试）。
 * 事务语义：每次改值在同一事务内完成钳制 [0,max]、等级所依赖的数值、锁定与 dirty 判定。
 * 锁定规则：value>=max 立即 locked；锁定后拒绝 POWER 的正负修改；
 * 合法外部负修改使 value<max 立即解除。非有限数一律拒绝且不传播 NaN。
 */
public final class PrimalstinctKernel {

    private PrimalstinctKernel() {
    }

    /** modify 的纯事务结果；applied=false 表示按规则拒绝（无任何状态变化）。 */
    public record ModifyResult(float value, boolean locked, boolean applied, boolean lockChanged) {
    }

    public static ModifyResult modify(float value, boolean locked, PrimalstinctSource source,
                                      float delta, float maxValue) {
        if (!Float.isFinite(value) || !Float.isFinite(delta) || maxValue <= 0.0f) {
            return new ModifyResult(sanitize(value, maxValue), locked, false, false);
        }
        // 满值锁定：拒绝“本能变化类”Power（正负皆拒，防 Power Action 自解锁）
        if (locked && source == PrimalstinctSource.POWER) {
            return new ModifyResult(value, locked, false, false);
        }
        // 满值处的正向外部修改：无处可加，保持锁定（负向照常走解锁路径）
        if (locked && delta > 0.0f && value >= maxValue) {
            return new ModifyResult(value, locked, false, false);
        }
        float raw = value + delta;
        if (!Float.isFinite(raw)) {
            return new ModifyResult(value, locked, false, false);
        }
        float newValue = clamp(raw, maxValue);
        boolean newLocked = newValue >= maxValue;
        return new ModifyResult(newValue, newLocked, true, newLocked != locked);
    }

    /** 速率合并（点/秒）：锁定时仅保留负向外部贡献（用于解除路径），POWER 增长满值即停。 */
    public static float mergeRate(float value, boolean locked, Iterable<Contribution> contributions) {
        float sum = 0.0f;
        for (Float perSource : mergeBySource(value, locked, contributions).values()) {
            sum += perSource;
        }
        if (!Float.isFinite(sum)) {
            return 0.0f;
        }
        return sum;
    }

    /** 按来源分组合并（点/秒），锁定过滤同 mergeRate；供 tick 分来源结算，防止外源被 POWER 拒改误伤。 */
    public static java.util.Map<PrimalstinctSource, Float> mergeBySource(
            float value, boolean locked, Iterable<Contribution> contributions) {
        java.util.EnumMap<PrimalstinctSource, Float> result = new java.util.EnumMap<>(PrimalstinctSource.class);
        for (Contribution contribution : contributions) {
            if (!Float.isFinite(contribution.pointsPerSecond())) {
                continue;
            }
            if (locked) {
                boolean negativeExternal = contribution.pointsPerSecond() < 0.0f
                        && contribution.source() != PrimalstinctSource.POWER;
                if (!negativeExternal) {
                    continue;
                }
            }
            result.merge(contribution.source(), contribution.pointsPerSecond(), Float::sum);
        }
        return result;
    }

    /**
     * tick 的分来源事务：POWER 部分在锁定时整段拒绝；外部部分在满值处忽略正向、负向参与解锁。
     * 输入为每来源的每 tick 增量（调用方已完成 点/秒→点/tick 换算）。
     */
    public static ModifyResult modifyMulti(float value, boolean locked,
                                           java.util.Map<PrimalstinctSource, Float> perTickDeltas,
                                           float maxValue) {
        if (!Float.isFinite(value) || maxValue <= 0.0f) {
            return new ModifyResult(sanitize(value, maxValue), locked, false, false);
        }
        float powerDelta = 0.0f;
        float externalDelta = 0.0f;
        for (java.util.Map.Entry<PrimalstinctSource, Float> entry : perTickDeltas.entrySet()) {
            float amount = entry.getValue();
            if (!Float.isFinite(amount)) {
                continue;
            }
            if (entry.getKey() == PrimalstinctSource.POWER) {
                powerDelta += amount;
            } else {
                externalDelta += amount;
            }
        }
        float delta = 0.0f;
        boolean touched = false;
        if (locked) {
            // POWER 正负皆拒（防 Power Action 自解锁）
            if (externalDelta > 0.0f && value >= maxValue) {
                externalDelta = 0.0f;  // 满值处正向外部：无处可加
            }
            delta = externalDelta;
            touched = externalDelta != 0.0f;
        } else {
            delta = powerDelta + externalDelta;
            touched = delta != 0.0f;
        }
        if (!touched) {
            return new ModifyResult(value, locked, false, false);
        }
        float raw = value + delta;
        if (!Float.isFinite(raw)) {
            return new ModifyResult(value, locked, false, false);
        }
        float newValue = clamp(raw, maxValue);
        boolean newLocked = newValue >= maxValue;
        return new ModifyResult(newValue, newLocked, true, newLocked != locked);
    }

    /** tick 合并后的持续变化：点/秒 → 每 tick 增量。 */
    public static float perTick(float pointsPerSecond) {
        return pointsPerSecond / 20.0f;
    }

    public static float clamp(float value, float maxValue) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(maxValue, value));
    }

    private static float sanitize(float value, float maxValue) {
        return Float.isFinite(value) ? clamp(value, maxValue) : 0.0f;
    }

    /** 速率贡献（稳定键去重；来源不可由数据包声明）。 */
    public record Contribution(String key, PrimalstinctSource source, float pointsPerSecond) {
    }
}
