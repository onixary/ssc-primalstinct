package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.endgame.service.RitualPortalService;

/**
 * 眷属实现03/08：原初传送门框架方块（末地传送门式 5×5 环，去四角共 12 块；缺角不影响判定）。
 * 破框时移除关联门面并置 BROKEN——避免孤立传送格；修框后再次开门需要新碎片（默认规则）。
 */
public class PrimalPortalFrameBlock extends EndgameModelBlock {

    /**
     * 剔除形状：决定相邻方块要不要把贴着框架这一面的面剔掉。
     *
     * 它必须和模型一样是"非完整方块"。默认的实现返回整格形状，邻居就会把框架当成
     * 不透明满方块，靠着它的那一面直接消失；而框架本身是 BER 渲染的，四根柱子之间
     * 那道缝看得见，于是旁边就露出一个洞。
     *
     * 注意这里只改剔除形状，碰撞体仍是整格：柱子之间的十字缝从顶面通到 Y=8，有 8 格深，
     * 真照模型开缝会变成一个掉进去爬不出来的口袋。
     */
    private static final VoxelShape CULL_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),   // 苔石脚 + 石身 + 收边
            Block.createCuboidShape(0.0, 8.0, 0.0, 7.0, 16.0, 7.0),    // 四根方柱，中间留 2 格缝
            Block.createCuboidShape(9.0, 8.0, 0.0, 16.0, 16.0, 7.0),
            Block.createCuboidShape(0.0, 8.0, 9.0, 7.0, 16.0, 16.0),
            Block.createCuboidShape(9.0, 8.0, 9.0, 16.0, 16.0, 16.0));

    public PrimalPortalFrameBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return CULL_SHAPE;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            RitualPortalService.breakPortalAround(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public static Settings frameSettings() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN).strength(50.0f, 1200.0f);
    }
}
