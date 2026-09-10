package net.onixary.sscPrimalstinct.endgame.data;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 眷属实现02：终局数据包（endgame/rituals/default.json + endgame/variants/*.json）的解析结果。
 * rituals/default.json：供物候选清单（物品ID/数量/权重）、结构ID、Info 键等仪式参数；
 * variants/*.json：源形态→目标形态映射表。
 * 内容参数全部视为“建议默认值”，正式内容冻结时复核（眷属实现01）。
 */
public final class EndgameRitualConfig {

    /** 单个供物候选：从清单中无放回抽选（三基座各 1 件，保证不同）。 */
    public record OfferingEntry(Identifier item, int count, int weight) {
    }

    public final int schemaVersion;
    public final Identifier ritualId;
    /** 主世界祭坛结构 ID（眷属实现04 的结构注册名）。 */
    public final Identifier structureId;
    /** 供物候选清单；三基座在结构生成期从此清单无放回抽取。 */
    public final List<OfferingEntry> offerings;
    /** 满级击杀触发原初残余的掉率（眷属实现07；1.0=必出，0=不出）。 */
    public final float remnantDropChance;
    /** “敌人”判定实体标签（眷属实现07；空=默认敌对怪物）。 */
    public final Identifier remnantTargetTag;
    /** 源形态→变体映射（按源 FormID 索引）。 */
    public final Map<Identifier, EndgameVariantMapping> variants;

    public EndgameRitualConfig(int schemaVersion, Identifier ritualId, Identifier structureId,
                               List<OfferingEntry> offerings,
                               float remnantDropChance, Identifier remnantTargetTag,
                               Map<Identifier, EndgameVariantMapping> variants) {
        this.schemaVersion = schemaVersion;
        this.ritualId = ritualId;
        this.structureId = structureId;
        this.offerings = Collections.unmodifiableList(offerings);
        this.remnantDropChance = remnantDropChance;
        this.remnantTargetTag = remnantTargetTag;
        this.variants = Collections.unmodifiableMap(variants);
    }
}
