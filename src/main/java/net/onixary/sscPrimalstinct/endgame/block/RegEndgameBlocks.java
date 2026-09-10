package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * 眷属实现03：终局方块与方块物品注册。
 * 方块物品进入本模组创造标签页（创造管理仍可放置/移除；生存侧仪式核心按建议规则抗破坏）。
 * 命名沿用白板：primal_pedestal / primal_energy_wire / primal_altar / spent_primal_altar /
 * primal_portal_frame / primal_portal / primal_conversion_platform。
 */
public final class RegEndgameBlocks {

    /** 创造标签页展示顺序。 */
    private static final List<Item> GROUP_ITEMS = new ArrayList<>();

    public static final PrimalPedestalBlock PRIMAL_PEDESTAL = register("primal_pedestal",
            new PrimalPedestalBlock(PrimalPedestalBlock.ritualSettings()));
    public static final PrimalEnergyWireBlock PRIMAL_ENERGY_WIRE = register("primal_energy_wire",
            new PrimalEnergyWireBlock(PrimalEnergyWireBlock.wireSettings()));
    public static final PrimalAltarBlock PRIMAL_ALTAR = register("primal_altar",
            new PrimalAltarBlock(PrimalAltarBlock.altarSettings()));
    public static final SpentPrimalAltarBlock SPENT_PRIMAL_ALTAR = register("spent_primal_altar",
            new SpentPrimalAltarBlock(SpentPrimalAltarBlock.spentSettings()));
    public static final PrimalPortalFrameBlock PRIMAL_PORTAL_FRAME = register("primal_portal_frame",
            new PrimalPortalFrameBlock(PrimalPortalFrameBlock.frameSettings()));
    public static final PrimalPortalBlock PRIMAL_PORTAL = register("primal_portal",
            new PrimalPortalBlock(PrimalPortalBlock.portalSettings()));
    public static final PrimalConversionPlatformBlock PRIMAL_CONVERSION_PLATFORM = register("primal_conversion_platform",
            new PrimalConversionPlatformBlock(PrimalConversionPlatformBlock.platformSettings()));

    private RegEndgameBlocks() {
    }

    private static <T extends Block> T register(String path, T block) {
        Identifier id = EndgameRules.id(path);
        Registry.register(Registries.BLOCK, id, block);
        BlockItem item = new BlockItem(block, new Item.Settings());
        Registry.register(Registries.ITEM, id, item);
        GROUP_ITEMS.add(item);
        return block;
    }

    public static List<Item> groupItems() {
        return GROUP_ITEMS;
    }

    public static void registerAll() {
        // 静态字段初始化即完成注册；此方法供主入口显式触发并登记 BlockEntityType
        RegEndgameBlockEntities.registerAll();
        // 创造标签页追加（组 entries 回调惰性读取列表，注册期追加即生效）
        net.onixary.sscPrimalstinct.items.RegPrimalstinctItems.appendEndgameItems(GROUP_ITEMS);
    }
}
