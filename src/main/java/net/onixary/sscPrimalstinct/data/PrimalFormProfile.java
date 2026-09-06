package net.onixary.sscPrimalstinct.data;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 卡02：单个形态的 Primalstinct 配置（data/&lt;ns&gt;/primalstinct/forms/*.json 的解析结果）。
 * 字段与白板卡02一致：schema_version/form_id/selectable/order/fallback_form/
 * base_powers{add,remove}/level_overrides/instinct_powers/diet_profile/sleep_profile。
 */
public final class PrimalFormProfile {

    public static final int SCHEMA_VERSION = 1;

    public final Identifier formId;
    public final boolean selectable;
    public final int order;
    /** 为 null 时动态解析：子形态→master，主形态→shape-shifter-curse:original_before_enable。 */
    public final @Nullable Identifier fallbackForm;
    public final List<Identifier> basePowersAdd;
    public final List<Identifier> basePowersRemove;
    /** form 级每级增量，叠加在公共等级表之上；键为等级 1..N。 */
    public final Map<Integer, PrimalLevels.LevelPowers> levelOverrides;
    /** 卡06/07 的本能条件 power；当前仅保存 ID 列表。 */
    public final List<Identifier> instinctPowers;
    /** 食性/睡眠档案引用（卡07/08、卡11 落地目标文件；本卡只做格式校验）。 */
    public final @Nullable Identifier dietProfile;
    public final @Nullable Identifier sleepProfile;
    /** 用于错误输出回溯源文件。 */
    public final String sourceFile;

    public PrimalFormProfile(Identifier formId, boolean selectable, int order,
                             @Nullable Identifier fallbackForm,
                             List<Identifier> basePowersAdd, List<Identifier> basePowersRemove,
                             Map<Integer, PrimalLevels.LevelPowers> levelOverrides,
                             List<Identifier> instinctPowers,
                             @Nullable Identifier dietProfile, @Nullable Identifier sleepProfile,
                             String sourceFile) {
        this.formId = formId;
        this.selectable = selectable;
        this.order = order;
        this.fallbackForm = fallbackForm;
        this.basePowersAdd = Collections.unmodifiableList(basePowersAdd);
        this.basePowersRemove = Collections.unmodifiableList(basePowersRemove);
        this.levelOverrides = Collections.unmodifiableMap(levelOverrides);
        this.instinctPowers = Collections.unmodifiableList(instinctPowers);
        this.dietProfile = dietProfile;
        this.sleepProfile = sleepProfile;
        this.sourceFile = sourceFile;
    }

    public static final class LevelOverride {
        public final Set<Identifier> add;
        public final Set<Identifier> remove;

        public LevelOverride(Set<Identifier> add, Set<Identifier> remove) {
            this.add = add;
            this.remove = remove;
        }
    }
}
