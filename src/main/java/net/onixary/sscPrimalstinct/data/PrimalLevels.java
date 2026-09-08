package net.onixary.sscPrimalstinct.data;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 卡02：公共等级表（data/&lt;ns&gt;/primalstinct/levels/default.json）。
 * 四个普通区间：L1=[0,25)、L2=[25,50)、L3=[50,75)、L4=[75,100)，L5=满值锁定。
 * 每级只声明 add/remove 增量；查询时按 0→当前级顺序累计展开（先 add 后 remove）。
 */
public final class PrimalLevels {

    public static final float DEFAULT_MAX_VALUE = 100.0f;
    public static final float[] DEFAULT_THRESHOLDS = {0.0f, 25.0f, 50.0f, 75.0f, 100.0f};

    public final float maxValue;
    public final boolean lockAtMax;
    /** thresholds[i] 是进入 level i+1 的下界；默认第一个阈值为 0，初始阶段就是 L1。 */
    public final float[] thresholds;

    /** 每级的增量声明（level 1..N），仅作数据保留；查询走 cumulative*。 */
    private final List<LevelPowers> levelIncrements;
    /** cumulativeAdd.get(level)/cumulativeRemove.get(level)：0→level 累计展开后的生效集合。 */
    private final List<Set<Identifier>> cumulativeAdd;
    private final List<Set<Identifier>> cumulativeRemove;

    private PrimalLevels(float maxValue, boolean lockAtMax, float[] thresholds,
                         List<LevelPowers> levelIncrements,
                         List<Set<Identifier>> cumulativeAdd, List<Set<Identifier>> cumulativeRemove) {
        this.maxValue = maxValue;
        this.lockAtMax = lockAtMax;
        this.thresholds = thresholds;
        this.levelIncrements = Collections.unmodifiableList(levelIncrements);
        this.cumulativeAdd = cumulativeAdd;
        this.cumulativeRemove = cumulativeRemove;
    }

    public int maxLevel() {
        return thresholds.length;
    }

    public int levelForValue(float value) {
        int level = 1;
        for (int i = 0; i < thresholds.length; i++) {
            if (value >= thresholds[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    public boolean isMaxValue(float value) {
        return value >= maxValue;
    }

    public LevelPowers incrementsFor(int level) {
        if (level <= 0 || level > levelIncrements.size()) {
            return LevelPowers.EMPTY;
        }
        return levelIncrements.get(level - 1);
    }

    public Set<Identifier> cumulativeAddFor(int level) {
        return cumulativeAdd.get(Math.max(0, Math.min(maxLevel(), level)));
    }

    public Set<Identifier> cumulativeRemoveFor(int level) {
        return cumulativeRemove.get(Math.max(0, Math.min(maxLevel(), level)));
    }

    public static PrimalLevels defaults() {
        Builder builder = new Builder(DEFAULT_MAX_VALUE, true, DEFAULT_THRESHOLDS.clone());
        for (int i = 1; i <= builder.levelCount(); i++) {
            builder.level(i); // 空增量
        }
        return builder.build();
    }

    /** 某一级的 add/remove 增量声明。 */
    public static final class LevelPowers {
        public static final LevelPowers EMPTY = new LevelPowers(Set.of(), Set.of());
        public final Set<Identifier> add;
        public final Set<Identifier> remove;

        public LevelPowers(Set<Identifier> add, Set<Identifier> remove) {
            this.add = Collections.unmodifiableSet(add);
            this.remove = Collections.unmodifiableSet(remove);
        }
    }

    /** 解析+累计展开；违反约束时通过 BuildError 抛出（消息中带文件路径由调用方拼接）。 */
    public static final class Builder {
        private final float maxValue;
        private final boolean lockAtMax;
        private final float[] thresholds;
        private final List<LevelPowers> increments = new ArrayList<>();

        public Builder(float maxValue, boolean lockAtMax, float[] thresholds) {
            this.maxValue = maxValue;
            this.lockAtMax = lockAtMax;
            this.thresholds = thresholds;
        }

        public int levelCount() {
            return thresholds.length;
        }

        public Builder level(int level, Set<Identifier> add, Set<Identifier> remove) {
            if (level != increments.size() + 1) {
                throw new BuildError("等级必须按 1..N 连续声明，收到 level " + level);
            }
            for (Identifier id : add) {
                if (remove.contains(id)) {
                    throw new BuildError("level " + level + " 同级冲突：" + id + " 同时出现在 add 与 remove");
                }
            }
            if (add.size() != add.stream().distinct().count() || remove.size() != remove.stream().distinct().count()) {
                throw new BuildError("level " + level + " 存在重复 power 声明");
            }
            increments.add(new LevelPowers(new HashSet<>(add), new HashSet<>(remove)));
            return this;
        }

        public Builder level(int level) {
            return level(level, Set.of(), Set.of());
        }

        public PrimalLevels build() {
            if (thresholds.length == 0) {
                throw new BuildError("thresholds 不能为空");
            }
            for (int i = 0; i < thresholds.length; i++) {
                if (!Float.isFinite(thresholds[i]) || thresholds[i] < 0 || thresholds[i] > maxValue) {
                    throw new BuildError("thresholds[" + i + "]=" + thresholds[i] + " 超出 [0, max_value]");
                }
                if (i > 0 && thresholds[i] <= thresholds[i - 1]) {
                    throw new BuildError("thresholds 必须严格递增");
                }
            }
            if (thresholds[thresholds.length - 1] != maxValue) {
                throw new BuildError("最后一个阈值必须等于 max_value（满值即最高级）");
            }
            if (increments.size() != thresholds.length) {
                throw new BuildError("levels 声明数量(" + increments.size() + ")与阈值数量(" + thresholds.length + ")不一致");
            }
            List<Set<Identifier>> cumAdd = new ArrayList<>();
            List<Set<Identifier>> cumRemove = new ArrayList<>();
            cumAdd.add(Set.of());
            cumRemove.add(Set.of());
            Set<Identifier> running = new HashSet<>();
            for (LevelPowers inc : increments) {
                running.addAll(inc.add);
                running.removeAll(inc.remove);
                cumAdd.add(Set.copyOf(running));
                cumRemove.add(Set.of()); // 展开后仅保留生效集合，remove 集不再单独需要
            }
            return new PrimalLevels(maxValue, lockAtMax, thresholds, increments, cumAdd, cumRemove);
        }
    }

    public static final class BuildError extends RuntimeException {
        public BuildError(String message) {
            super(message);
        }
    }
}
