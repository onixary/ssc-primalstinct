package net.onixary.sscPrimalstinct.endgame.data;

import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 眷属实现02：终局配置生命周期管理（与 PrimalRosterManager 同模式）。
 * 解析（reload 线程）产出候选；引用校验与原子交换在 SERVER_STARTED / END_DATA_PACK_RELOAD
 * 进行（此时 SSC Form / 物品注册表可用），全部成功才替换运行快照；热重载失败保留上一版。
 * 已生成祭坛的供物写在基座 BE NBT 中，不因 reload 重新抽取。
 */
public final class EndgameRosterManager {

    /** 内置默认配置（无 endgame 数据包时的兜底；无变体映射 → 终局转化停用）。 */
    private static final EndgameRitualConfig EMPTY = new EndgameRitualConfig(
            1, Identifier.of(SSCPrimalstinct.MOD_ID, "default"),
            Identifier.of(SSCPrimalstinct.MOD_ID, "primal_altar"),
            List.of(), 0.0f, null, java.util.Map.of());

    private static volatile EndgameRitualConfig active = EMPTY;
    private static volatile @Nullable EndgameRitualConfig pending;
    private static volatile @Nullable List<String> lastParseErrors;
    private static volatile @Nullable List<String> lastValidationErrors;

    private EndgameRosterManager() {
    }

    public static EndgameRitualConfig active() {
        return active;
    }

    public static @Nullable List<String> lastParseErrors() {
        return lastParseErrors;
    }

    public static @Nullable List<String> lastValidationErrors() {
        return lastValidationErrors;
    }

    /** 由 EndgameReloadListener 调用：结构解析全部成功。 */
    static void onParsed(EndgameRitualConfig candidate) {
        pending = candidate;
        lastParseErrors = null;
        SSCPrimalstinct.LOGGER.info("[primalstinct] 终局配置解析完成：变体映射 {} 个，供物候选 {} 个（待引用校验）",
                candidate.variants.size(), candidate.offerings.size());
    }

    /** 由 EndgameReloadListener 调用：结构解析失败。 */
    static void onParseFailed(List<String> errors) {
        pending = null;
        lastParseErrors = List.copyOf(errors);
    }

    /** SERVER_STARTED / END_DATA_PACK_RELOAD 时调用；幂等。 */
    public static synchronized void validateAndSwap() {
        EndgameRitualConfig candidate = pending;
        if (candidate == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        validateReferences(candidate, errors);
        if (!errors.isEmpty()) {
            for (String error : errors) {
                SSCPrimalstinct.LOGGER.error("[primalstinct] 终局配置引用校验失败，保留上一版: {}", error);
            }
            lastValidationErrors = List.copyOf(errors);
            pending = null;
            return;
        }
        active = candidate;
        pending = null;
        lastValidationErrors = null;
        SSCPrimalstinct.LOGGER.info("[primalstinct] 终局配置已生效：变体映射 {} 个，供物候选 {} 个",
                active.variants.size(), active.offerings.size());
    }

    private static void validateReferences(EndgameRitualConfig config, List<String> errors) {
        // 供物候选：必须解析为已注册物品
        for (EndgameRitualConfig.OfferingEntry entry : config.offerings) {
            if (Registries.ITEM.containsId(entry.item())) {
                continue;
            }
            // 允许引用本模组尚未注册的占位 ID 时应显式提示；未注册物品直接报错
            if (Items.AIR == Registries.ITEM.get(entry.item())) {
                errors.add("rituals/default.json offerings: 物品 " + entry.item() + " 未注册");
            }
        }
        // 变体映射：source/target 必须都是已注册 SSC Form；无循环（变体不得再作为 source 链的一环）
        Set<Identifier> targets = new HashSet<>();
        for (EndgameVariantMapping mapping : config.variants.values()) {
            if (SSCAdapter.formInfo(mapping.sourceForm) == null) {
                errors.add(mapping.sourceFile + ": source_form " + mapping.sourceForm + " 在 SSC 中不存在");
            }
            SSCAdapter.SscFormInfo targetInfo = SSCAdapter.formInfo(mapping.targetForm);
            if (targetInfo == null) {
                errors.add(mapping.sourceFile + ": target_form " + mapping.targetForm + " 在 SSC 中不存在"
                        + "（变体必须在 SSC 形态注册表中注册，见 RegPrimalVariants）");
            } else if (config.variants.containsKey(mapping.targetForm)) {
                errors.add(mapping.sourceFile + ": target_form " + mapping.targetForm
                        + " 同时也是其它映射的 source_form，存在映射链循环");
            }
            if (!targets.add(mapping.targetForm)) {
                errors.add(mapping.sourceFile + ": 重复的 target_form " + mapping.targetForm);
            }
        }
    }

    // ---------- 查询 ----------

    /** 源形态→变体映射；无映射返回 null。 */
    public static @Nullable EndgameVariantMapping variantFor(Identifier sourceForm) {
        return active.variants.get(sourceForm);
    }

    /** 该形态是否是某个映射的目标（即它本身已是原始变体）。 */
    public static boolean isVariant(Identifier formId) {
        for (EndgameVariantMapping mapping : active.variants.values()) {
            if (mapping.targetForm.equals(formId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasVariants() {
        return !active.variants.isEmpty();
    }
}
