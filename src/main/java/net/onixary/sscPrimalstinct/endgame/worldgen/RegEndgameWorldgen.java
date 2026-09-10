package net.onixary.sscPrimalstinct.endgame.worldgen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;

/**
 * 眷属实现04：终局结构注册（STRUCTURE_TYPE / STRUCTURE_PIECE）。
 * 数据包侧：worldgen/structure/primal_altar.json（type 引用）、
 * worldgen/structure_set/primal_altar.json（random_spread 分布）、
 * minecraft:has_structure/primal_altar 生物群系标签。
 * 恒定布局模板：data/ssc-primalstinct/structures/primal_altar.nbt（结构方块导出；缺失时内置回退布局）。
 * /locate structure ssc-primalstinct:primal_altar 可直接定位（结构定位系统原生支持）。
 */
public final class RegEndgameWorldgen {

    /** 恒定结构模板 ID（结构方块导出名 ssc-primalstinct:primal_altar）。 */
    public static final Identifier TEMPLATE_ID = Identifier.of(SSCPrimalstinct.MOD_ID, "primal_altar");

    public static final StructureType<PrimalAltarStructure> PRIMAL_ALTAR_TYPE =
            Registry.register(Registries.STRUCTURE_TYPE,
                    Identifier.of(SSCPrimalstinct.MOD_ID, "primal_altar"),
                    () -> PrimalAltarStructure.CODEC);

    public static final StructurePieceType PRIMAL_ALTAR_PIECE =
            Registry.register(Registries.STRUCTURE_PIECE,
                    Identifier.of(SSCPrimalstinct.MOD_ID, "primal_altar"),
                    (context, nbt) -> new PrimalAltarStructurePiece(nbt));

    private RegEndgameWorldgen() {
    }

    public static void registerAll() {
        // 注册在静态字段完成；此入口供主入口显式触发类加载（幂等）
    }

    /** 数据包 structure_id 约定（与 endgame/rituals/default.json 的 structure_id 一致）。 */
    public static Identifier structureId() {
        return EndgameRules.id("primal_altar");
    }
}
