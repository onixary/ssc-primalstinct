package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * 眷属实现03：原初能量导线。
 * 视觉参考原版红石线，但只存连接外观与自有激活状态（LIT）；
 * 不输出红石、不响应原版 power（isEmittingRedstonePower/getWeakRedstonePower 均为否）。
 * 首版仅支持模板中的水平路径（眷属实现06 的三条固定路径方案）；
 * 能量来自所属基座的 fulfilled 状态，由祭坛控制器重算时驱动。
 */
public class PrimalEnergyWireBlock extends EndgameModelBlock {

    public static final BooleanProperty LIT = BooleanProperty.of("lit");

    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

    public PrimalEnergyWireBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /** 明确不响应/输出任何红石（专用导线，眷属实现03验收项）。 */
    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return false;
    }

    public static Settings wireSettings() {
        return FabricBlockSettings.copyOf(Blocks.SMOOTH_STONE)
                .strength(1.5f, 6.0f)
                .luminance(state -> state.get(LIT) ? 7 : 0)
                .nonOpaque();
    }
}
