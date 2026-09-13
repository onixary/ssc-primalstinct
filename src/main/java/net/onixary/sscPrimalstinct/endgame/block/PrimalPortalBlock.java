package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
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
public class PrimalPortalBlock extends EndgameModelBlock {

    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public PrimalPortalBlock(Settings settings) {
        super(settings);
    }

    /** 门面使用原版末地门 BE：复用 EndPortalBlockEntityRenderer 的星野效果与 Y 轴面判定。 */
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrimalPortalBlockEntity(pos, state);
    }

    /** 不渲染方块模型，世界内显示完全由末地门 BER 绘制（同原版 end_portal）。 */
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    /** 原版末地门的门面烟雾氛围粒子。 */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 0.45;
        double z = pos.getZ() + random.nextDouble();
        world.addParticle(net.minecraft.particle.ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!(world instanceof ServerWorld serverWorld) || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        net.onixary.sscPrimalstinct.endgame.service.EndgameTeleportService.onPortalContact(player, serverWorld, pos);
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
