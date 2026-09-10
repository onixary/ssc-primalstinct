package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.service.TransformationService;

import java.util.List;

/**
 * 眷属实现13：转化台检测（服务端）。
 * 每 PLATFORM_TICK_INTERVAL tick 扫描“脚部落在台顶支撑区域”的玩家：
 * 检测区域只覆盖台面上方一小段（PLATFORM_DETECT_HEIGHT），不能把楼上玩家算进来。
 * 条件全部满足才调用 TransformationService.tryStart（资格判定的唯一入口在
 * EndgameEligibility，本类不做第二套规则）。
 */
public class PrimalConversionPlatformBlockEntity extends BlockEntity {

    public static final Identifier BLOCK_ENTITY_ID = EndgameRules.id("primal_conversion_platform");

    private int tickCounter = 0;

    public PrimalConversionPlatformBlockEntity(BlockPos pos, BlockState state) {
        super(RegEndgameBlockEntities.PRIMAL_CONVERSION_PLATFORM, pos, state);
    }

    public static void tick(net.minecraft.world.World world, BlockPos pos, BlockState state,
                            PrimalConversionPlatformBlockEntity entity) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        entity.tickCounter++;
        if (entity.tickCounter % EndgameRules.PLATFORM_TICK_INTERVAL != 0) {
            return;
        }
        // 检测区域覆盖台面上方一层（台面碰撞体 12px，玩家脚部落在 pos.y+0.75 附近），
        // 再以“支撑方块是本转化台”核实——楼上/邻块站立的玩家不计入
        Box detectBox = new Box(
                pos.getX(), pos.getY() + 0.6, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.6, pos.getZ() + 1.0);
        List<PlayerEntity> candidates = serverWorld.getEntitiesByClass(PlayerEntity.class, detectBox,
                player -> player != null && player.isAlive() && player.isOnGround());
        for (PlayerEntity player : candidates) {
            BlockPos support = BlockPos.ofFloored(player.getX(), player.getY() - 0.2, player.getZ());
            if (!pos.equals(support)) {
                continue;
            }
            if (player instanceof ServerPlayerEntity serverPlayer) {
                TransformationService.tryStartFromPlatform(serverPlayer, pos);
            }
        }
    }
}
