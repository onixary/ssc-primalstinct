package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * 眷属实现03：原初基座。
 * FACING 决定供物图标的展示面（BER 渲染）；FULFILLED 为献祭完成标记。
 * 需求物与实例绑定存 PrimalPedestalBlockEntity（结构生成期写入，不随 reload 重抽）。
 * 仪式核心建议规则：生存不可破坏、抗爆、不可被活塞搬运（后续开放搬运须连带迁移实例ID）。
 */
public class PrimalPedestalBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty FULFILLED = BooleanProperty.of("fulfilled");

    private static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 10.0, 14.0);

    public PrimalPedestalBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH).with(FULFILLED, false));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrimalPedestalBlockEntity(pos, state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, FULFILLED);
    }

    @Override
    public @org.jetbrains.annotations.Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    public static Settings ritualSettings() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f).luminance(state -> 4);
    }
}
