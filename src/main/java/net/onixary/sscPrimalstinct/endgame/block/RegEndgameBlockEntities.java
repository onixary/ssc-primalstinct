package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 眷属实现03：终局方块实体注册。
 * 服务端 tick 经各方块 getTicker（BlockEntityProvider）登记；专用服务器不加载 renderer。
 */
public final class RegEndgameBlockEntities {

    public static final BlockEntityType<EndgameModelBlockEntity> ENDGAME_MODEL =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    net.onixary.sscPrimalstinct.endgame.EndgameRules.id("endgame_model"),
                    BlockEntityType.Builder.create(EndgameModelBlockEntity::new,
                            RegEndgameBlocks.PRIMAL_ENERGY_WIRE, RegEndgameBlocks.SPENT_PRIMAL_ALTAR,
                            RegEndgameBlocks.PRIMAL_PORTAL_FRAME).build(null));

    /** 门面 BE：继承 EndPortalBlockEntity 复用原版末地门渲染（星野着色器 + Y 轴面判定）。 */
    public static final BlockEntityType<PrimalPortalBlockEntity> PRIMAL_PORTAL =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    PrimalPortalBlockEntity.BLOCK_ENTITY_ID,
                    BlockEntityType.Builder.create(PrimalPortalBlockEntity::new,
                            RegEndgameBlocks.PRIMAL_PORTAL).build(null));

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
        EndgameModelMigration.register();
    }
}
