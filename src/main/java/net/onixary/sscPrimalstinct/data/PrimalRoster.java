package net.onixary.sscPrimalstinct.data;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 卡02/08：运行时名单快照（不可变）。替换以整体原子交换完成——错误 JSON 不会产生半更新配置。
 * revision 单调递增，供后续同步协议（卡03/12）判断配置世代。
 */
public final class PrimalRoster {

    public static final PrimalRoster EMPTY = new PrimalRoster(0, PrimalLevels.defaults(), Map.of(), Map.of(), List.of());

    public final int revision;
    public final PrimalLevels levels;
    public final Map<Identifier, PrimalFormProfile> profiles;
    /** 食性档案（卡08），key 为 diet_profile 引用的 id。 */
    public final Map<Identifier, PrimalDiet> diets;
    /** 有序可选名单：按 (order, formId) 排序，顺序确定。 */
    public final List<PrimalFormProfile> orderedSelectable;

    public PrimalRoster(int revision, PrimalLevels levels, Map<Identifier, PrimalFormProfile> profiles,
                        Map<Identifier, PrimalDiet> diets) {
        this(revision, levels, profiles, diets, buildOrderedSelectable(profiles));
    }

    private PrimalRoster(int revision, PrimalLevels levels, Map<Identifier, PrimalFormProfile> profiles,
                         Map<Identifier, PrimalDiet> diets, List<PrimalFormProfile> orderedSelectable) {
        this.revision = revision;
        this.levels = levels;
        this.profiles = profiles;
        this.diets = diets;
        this.orderedSelectable = orderedSelectable;
    }

    private static List<PrimalFormProfile> buildOrderedSelectable(Map<Identifier, PrimalFormProfile> profiles) {
        List<PrimalFormProfile> list = new ArrayList<>();
        for (PrimalFormProfile profile : profiles.values()) {
            if (profile.selectable) {
                list.add(profile);
            }
        }
        list.sort(Comparator.comparingInt((PrimalFormProfile p) -> p.order).thenComparing(p -> p.formId));
        return List.copyOf(list);
    }

    public static Map<Identifier, PrimalFormProfile> linkedCopy(Map<Identifier, PrimalFormProfile> profiles) {
        return new LinkedHashMap<>(profiles);
    }
}
