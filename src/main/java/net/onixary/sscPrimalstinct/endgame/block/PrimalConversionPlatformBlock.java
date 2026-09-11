package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * 眷属实现03/13：原初转化台。
 * 服务端由 PrimalConversionPlatformBlockEntity 检测“脚部落在台顶支撑区域”的玩家
 * （名单内形态 + 实际最高级 + 下蹲 + 映射有效 + 无运行会话 → 触发变形）。
 * 不绑定维度、不要求化身实体存在——主世界放置同样有效（设计稿“原初转化台”）。
 */
public class PrimalConversionPlatformBlock extends Block implements BlockEntityProvider {

    /**
     * 半砖：轮廓与碰撞都取原版下半砖的 16×8×16。
     * 台面上方悬浮的符文阵半径 1 格、比方块边界各挑出半格，纯粹是装饰，不参与碰撞。
     */
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);

    public PrimalConversionPlatformBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrimalConversionPlatformBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        BlockEntityTicker<PrimalConversionPlatformBlockEntity> ticker = PrimalConversionPlatformBlockEntity::tick;
        return checkType(type, RegEndgameBlockEntities.PRIMAL_CONVERSION_PLATFORM, ticker);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }



    /** 本地 ticker 类型核对（Yarn 1.20.1 无 BlockWithEntity.validateTicker，参照 SSC checkType 模式）。 */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityTicker<T> checkType(
            BlockEntityType<T> given, BlockEntityType<?> expected, BlockEntityTicker<?> ticker) {
        return given == expected ? (BlockEntityTicker<T>) ticker : null;
    }

    public static Settings platformSettings() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f).luminance(state -> 6);
    }
}
