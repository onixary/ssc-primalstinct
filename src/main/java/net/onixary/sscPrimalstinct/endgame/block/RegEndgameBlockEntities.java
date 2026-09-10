package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 眷属实现03：终局方块实体注册。
 * 服务端 tick 经各方块 getTicker（BlockEntityProvider）登记；专用服务器不加载 renderer。
 */
public final class RegEndgameBlockEntities {

    public static final BlockEntityType<PrimalPedestalBlockEntity> PRIMAL_PEDESTAL =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    PrimalPedestalBlockEntity.BLOCK_ENTITY_ID,
                    BlockEntityType.Builder.create(PrimalPedestalBlockEntity::new,
                            RegEndgameBlocks.PRIMAL_PEDESTAL).build(null));

    public static final BlockEntityType<PrimalAltarBlockEntity> PRIMAL_ALTAR =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    PrimalAltarBlockEntity.BLOCK_ENTITY_ID,
                    BlockEntityType.Builder.create(PrimalAltarBlockEntity::new,
                            RegEndgameBlocks.PRIMAL_ALTAR).build(null));

    public static final BlockEntityType<PrimalConversionPlatformBlockEntity> PRIMAL_CONVERSION_PLATFORM =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    PrimalConversionPlatformBlockEntity.BLOCK_ENTITY_ID,
                    BlockEntityType.Builder.create(PrimalConversionPlatformBlockEntity::new,
                            RegEndgameBlocks.PRIMAL_CONVERSION_PLATFORM).build(null));

    private RegEndgameBlockEntities() {
    }

    public static void registerAll() {
        // 注册在静态字段完成；此入口供主入口显式触发类加载（幂等）
    }
}
