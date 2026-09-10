package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;


/**
 * 眷属实现03/08/10：原初传送门门面（可穿越，填充 3×3 内孔）。
 * 接触门面只调 EndgameTeleportService（服务端碰撞判定；外部=进入化身维度固定出生点，
 * 维度内=按接触者个人锚点返回）。主副手/重复接触由传送服务的过门冷却合并。
 */
public class PrimalPortalBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public PrimalPortalBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient() || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        net.onixary.sscPrimalstinct.endgame.service.EndgameTeleportService.onPortalContact(player);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // 可穿越（无碰撞）；轮廓形状保留用于选中方块的视觉反馈
        return context.isDescending() ? SHAPE : Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 0.0, 16.0);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            net.onixary.sscPrimalstinct.endgame.service.RitualPortalService.breakPortalAround(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public static Settings portalSettings() {
        return FabricBlockSettings.copyOf(Blocks.NETHER_PORTAL)
                .strength(-1.0f, 3600000.0f)
                .luminance(state -> 11)
                .nonOpaque();
    }
}
