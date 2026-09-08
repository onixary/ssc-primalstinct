package net.onixary.sscPrimalstinct.data;

import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 卡02：名单生命周期管理。
 * 解析（reload 线程）产出候选快照；引用校验与原子交换在 SSC Form/Apoli Power 注册数据
 * 可用之后进行（SERVER_STARTED 与 END_DATA_PACK_RELOAD），全部成功才替换运行快照，
 * 热重载失败保留上一版。首次加载无可选形态时输出明确启动错误。
 */
public final class PrimalRosterManager {

    public static final Identifier DEFAULT_MAIN_FALLBACK = Identifier.of("shape-shifter-curse", "original_before_enable");

    private static volatile PrimalRoster active = PrimalRoster.EMPTY;
    private static volatile @Nullable PrimalRoster pending;
    private static volatile @Nullable List<String> lastParseErrors;
    private static volatile @Nullable List<String> lastValidationErrors;

    private PrimalRosterManager() {
    }

    public static PrimalRoster active() {
        return active;
    }

    public static @Nullable List<String> lastParseErrors() {
        return lastParseErrors;
    }

    public static @Nullable List<String> lastValidationErrors() {
        return lastValidationErrors;
    }

    /** 由 PrimalProfileReloadListener 调用：结构解析全部成功。 */
    static void onParsed(PrimalLevels levels, Map<Identifier, PrimalFormProfile> profiles,
                         Map<Identifier, PrimalDiet> diets) {
        pending = new PrimalRoster(active.revision + 1, levels, PrimalRoster.linkedCopy(profiles), Map.copyOf(diets));
        lastParseErrors = null;
        SSCPrimalstinct.LOGGER.info("[primalstinct] 已解析 {} 个形态配置、{} 个食性档案（revision {} 候选，待引用校验）",
                profiles.size(), diets.size(), pending.revision);
    }

    /** 由 PrimalProfileReloadListener 调用：结构解析失败。 */
    static void onParseFailed(List<String> errors) {
        pending = null;
        lastParseErrors = List.copyOf(errors);
    }

    /** SERVER_STARTED / END_DATA_PACK_RELOAD 时调用；幂等。 */
    public static synchronized void validateAndSwap() {
        PrimalRoster candidate = pending;
        if (candidate == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        validateReferences(candidate, errors);
        if (!errors.isEmpty()) {
            for (String error : errors) {
                SSCPrimalstinct.LOGGER.error("[primalstinct] 引用校验失败，保留 revision {} 配置: {}",
                        active.revision, error);
            }
            lastValidationErrors = List.copyOf(errors);
            pending = null;
            return;
        }
        active = candidate;
        pending = null;
        lastValidationErrors = null;
        SSCPrimalstinct.LOGGER.info("[primalstinct] 名单已生效: revision {}, 可选形态 {}, 等级 L1..L{}（阈值 {}）",
                active.revision, active.orderedSelectable.size(), active.levels.maxLevel(),
                arrayToString(active.levels.thresholds));

        if (active.orderedSelectable.isEmpty()
                && net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig.chooseFormOnStart()) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 启动错误：当前没有任何可选形态（selectable=true）的合法配置。"
                    + "请检查 data/*/primalstinct/forms/ 数据包；选择界面（卡12/13）无法在该状态下工作。");
        }
    }

    private static void validateReferences(PrimalRoster roster, List<String> errors) {
        for (PrimalFormProfile profile : roster.profiles.values()) {
            SSCAdapter.SscFormInfo info = SSCAdapter.formInfo(profile.formId);
            if (info == null) {
                errors.add(profile.sourceFile + ": form_id " + profile.formId + " 在 SSC 中不存在（FormID 与 OriginID 不能混用）");
                continue;
            }
            validateFallbackChain(profile, info, errors);
            validatePowers("base_powers.add", profile.basePowersAdd, profile, errors);
            validatePowers("base_powers.remove", profile.basePowersRemove, profile, errors);
            for (Map.Entry<Integer, PrimalLevels.LevelPowers> entry : profile.levelOverrides.entrySet()) {
                validatePowers("level_overrides." + entry.getKey() + ".add", List.copyOf(entry.getValue().add), profile, errors);
                validatePowers("level_overrides." + entry.getKey() + ".remove", List.copyOf(entry.getValue().remove), profile, errors);
            }
            validatePowers("instinct_powers", profile.instinctPowers, profile, errors);
            // 卡08：diet 引用软校验——缺失告警不阻断（旧样本档案逐步补齐）
            if (profile.dietProfile != null && !roster.diets.containsKey(profile.dietProfile)) {
                SSCPrimalstinct.LOGGER.warn("[primalstinct] {} 的 diet_profile {} 尚无对应档案（{}），食性行为由 Power 决定",
                        profile.formId, profile.dietProfile, profile.sourceFile);
            }
        }
        validatePowers("levels/default.json levels.add",
                collectLevelPowers(roster.levels, true), null, errors);
        validatePowers("levels/default.json levels.remove",
                collectLevelPowers(roster.levels, false), null, errors);
    }

    private static void validateFallbackChain(PrimalFormProfile profile, SSCAdapter.SscFormInfo info, List<String> errors) {
        // 兜底解析：显式 fallback_form → 子形态跟随 master → 主形态默认 original_before_enable
        Identifier target = profile.fallbackForm != null ? profile.fallbackForm
                : (info.masterFormId() != null ? info.masterFormId() : DEFAULT_MAIN_FALLBACK);
        Set<Identifier> visited = new HashSet<>();
        visited.add(profile.formId);
        Identifier current = target;
        while (current != null) {
            if (!visited.add(current)) {
                errors.add(profile.sourceFile + ": 兜底链存在循环（" + profile.formId + " → ... → " + current + "）");
                return;
            }
            SSCAdapter.SscFormInfo targetInfo = SSCAdapter.formInfo(current);
            if (targetInfo == null) {
                String where = profile.fallbackForm != null ? "fallback_form" : "继承的 master 兜底";
                errors.add(profile.sourceFile + ": " + where + " " + current + " 在 SSC 中不存在");
                return;
            }
            if (!targetInfo.subForm()) {
                return; // 到达普通主形态，合法
            }
            current = targetInfo.masterFormId();
        }
        errors.add(profile.sourceFile + ": 兜底链未能解析到普通主形态（" + profile.formId + "）");
    }

    private static void validatePowers(String where, List<Identifier> powers,
                                       @Nullable PrimalFormProfile profile, List<String> errors) {
        for (Identifier power : powers) {
            if (!PowerTypeRegistry.contains(power)) {
                String source = profile != null ? profile.sourceFile : where;
                errors.add(source + ": " + where + " 引用的 power " + power + " 不在 Apoli PowerTypeRegistry 中");
            }
        }
    }

    private static List<Identifier> collectLevelPowers(PrimalLevels levels, boolean add) {
        List<Identifier> result = new ArrayList<>();
        for (int level = 1; level <= levels.maxLevel(); level++) {
            PrimalLevels.LevelPowers powers = levels.incrementsFor(level);
            result.addAll(add ? powers.add : powers.remove);
        }
        return result;
    }

    /**
     * 子形态按自身 FormID 查 profile；缺省时继承 master profile，再应用子形态覆盖（卡02）。
     * 返回 null 表示该形态（及其 master 链）均无 profile。
     */
    public static @Nullable PrimalFormProfile resolve(Identifier formId) {
        Set<Identifier> visited = new HashSet<>();
        Identifier current = formId;
        PrimalFormProfile own = null;
        while (current != null && visited.add(current)) {
            PrimalFormProfile profile = active.profiles.get(current);
            if (profile != null) {
                own = profile;
                break;
            }
            SSCAdapter.SscFormInfo info = SSCAdapter.formInfo(current);
            current = (info != null && info.subForm()) ? info.masterFormId() : null;
        }
        if (own == null) {
            return null;
        }
        // 子形态覆盖：仅补全未显式声明的继承字段（fallback/食性/睡眠）
        if (!own.formId.equals(formId)) {
            SSCAdapter.SscFormInfo info = SSCAdapter.formInfo(formId);
            boolean needsMasterFallback = own.fallbackForm == null && info != null && info.subForm();
            if (needsMasterFallback || own.dietProfile == null || own.sleepProfile == null) {
                return inheritMissing(own, formId);
            }
        }
        return own;
    }

    private static PrimalFormProfile inheritMissing(PrimalFormProfile masterProfile, Identifier subFormId) {
        return new PrimalFormProfile(
                subFormId,
                masterProfile.selectable,
                masterProfile.order,
                masterProfile.fallbackForm,
                masterProfile.basePowersAdd,
                masterProfile.basePowersRemove,
                masterProfile.levelOverrides,
                masterProfile.instinctPowers,
                masterProfile.dietProfile,
                masterProfile.sleepProfile,
                masterProfile.sourceFile + " → 继承至 " + subFormId);
    }

    private static String arrayToString(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i]);
        }
        return sb.append("]").toString();
    }
}
