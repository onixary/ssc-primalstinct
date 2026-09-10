package net.onixary.sscPrimalstinct.endgame.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import net.onixary.sscPrimalstinct.endgame.state.EndgameWorldState;
import net.onixary.sscPrimalstinct.items.RegPrimalstinctItems;

import java.util.List;

/**
 * 祷属实现08：门框检测与碎片开门（末地传送门式 5×5）。
 * 框形状：5×5 外环去掉四角（12 个门框方块，缺角不影响判定），内孔 3×3；
 * 门面关闭时为空气，开启后由 PrimalPortalBlock 填充内孔。
 *
 * <p>检测入口：每 5 tick 以在线玩家为锚（暮色森林 TFTickHandler 模式），收集附近镇静碎片
 * ItemEntity；由物品所在格推出 9 个候选内孔原点，逐个验证：
 * 12 个非角环位全部为原初门框、内孔 3×3 全部可替换（空气/液体）、且当前未开启。
 * 全部通过才进入开门事务：填内孔门面 → 消耗 1 个碎片 → 粒子/音效 → 记录 OPEN。
 * 中途任一失败不消耗（不能仅见碎片便消耗，再发现门不成立——眷属实现08）。
 * 开门不要求开门者/过门者满级；已开启不再消费；破框清门面并置 BROKEN，修框后再次开门需要新碎片。</p>
 */
public final class RitualPortalService {

    private static final int SCAN_INTERVAL_TICKS = 5;
    /** 玩家锚定扫描半径（碎片在玩家附近才检测）。 */
    private static final double SCAN_RADIUS = 10.0;

    private RitualPortalService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RitualPortalService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld world)) {
                continue;
            }
            List<ItemEntity> fragments = world.getEntitiesByClass(ItemEntity.class,
                    player.getBoundingBox().expand(SCAN_RADIUS),
                    item -> item.isAlive() && item.getStack().isOf(RegPrimalstinctItems.SEDATIVE_FRAGMENT));
            for (ItemEntity item : fragments) {
                if (tryOpenAt(world, item)) {
                    return;  // 每 tick 至多开一扇（防同帧批量消耗）
                }
            }
        }
    }

    /** 以碎片所在格为内孔候选，验证并执行开门事务；返回是否成功开门。 */
    private static boolean tryOpenAt(ServerWorld world, ItemEntity item) {
        BlockPos itemPos = item.getBlockPos();
        // 物品可能停在孔内任意一格：枚举覆盖该格的全部 3×3 内孔原点
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -2; dz <= 0; dz++) {
                BlockPos innerOrigin = itemPos.add(dx, 0, dz);
                if (tryOpen(world, item, innerOrigin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 开门事务：预检（环完整/内孔可替换/未开启）→ 填门面 → 消耗 1 碎片 → 表现与状态。 */
    private static boolean tryOpen(ServerWorld world, ItemEntity item, BlockPos innerOrigin) {
        // 1) 12 个非角环位全部为原初门框（缺角=四角无要求）
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 3; z++) {
                boolean border = x == -1 || x == 3 || z == -1 || z == 3;
                boolean corner = (x == -1 || x == 3) && (z == -1 || z == 3);
                if (!border || corner) {
                    continue;
                }
                if (!world.getBlockState(innerOrigin.add(x, 0, z)).isOf(RegEndgameBlocks.PRIMAL_PORTAL_FRAME)) {
                    return false;
                }
            }
        }
        // 2) 内孔 3×3 全部可替换且未开启
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                BlockState inner = world.getBlockState(innerOrigin.add(x, 0, z));
                if (!inner.isAir() && !inner.getFluidState().isEmpty()) {
                    return false;  // 堵孔不开门（眷属实现08）
                }
            }
        }
        // 3) 开门事务：填门面（全部成功才消耗）
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                world.setBlockState(innerOrigin.add(x, 0, z),
                        RegEndgameBlocks.PRIMAL_PORTAL.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        // 4) 消耗 1 个碎片（64 只扣 1）
        ItemStack stack = item.getStack();
        stack.decrement(1);
        item.setStack(stack);
        // 5) 表现 + 状态记录（CENTER = 内孔中心；OPEN 后不再消费）
        BlockPos center = innerOrigin.add(1, 0, 1);
        EndgameWorldState.get(world).setPortalState(world, center, EndgameWorldState.PortalState.OPEN);
        world.playSound(null, center, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.6f, 1.4f);
        world.spawnParticles(ParticleTypes.PORTAL,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                60, 1.2, 0.6, 1.2, 0.05);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 原初传送门开启：center={}（碎片实体 {}）", center, item.getId());
        return true;
    }

    /** 破框/破门面时清理关联门面并置 BROKEN（由方块 onStateReplaced 调用）。 */
    /** 重入守卫：清理门面时，被移除门面自身的 onStateReplaced 不再级联。 */
    private static boolean cleaning = false;

    public static void breakPortalAround(ServerWorld world, BlockPos changed) {
        if (cleaning) {
            return;
        }
        cleaning = true;
        try {
            boolean removed = false;
            BlockPos clusterMin = null;
            // 门面只存在于本开口的 3×3 内：7×7 覆盖任意相对位置
            for (BlockPos pos : BlockPos.iterate(changed.add(-3, -1, -3), changed.add(3, 1, 3))) {
                if (world.getBlockState(pos).isOf(RegEndgameBlocks.PRIMAL_PORTAL)) {
                    world.removeBlock(pos, false);
                    removed = true;
                    clusterMin = clusterMin == null ? pos.toImmutable()
                            : new BlockPos(Math.min(clusterMin.getX(), pos.getX()), pos.getY(), Math.min(clusterMin.getZ(), pos.getZ()));
                }
            }
            if (removed && clusterMin != null) {
                BlockPos center = clusterMin.add(1, 0, 1);
                EndgameWorldState.get(world).setPortalState(world, center, EndgameWorldState.PortalState.BROKEN);
                SSCPrimalstinct.LOGGER.info("[primalstinct] 原初传送门破损：center={}（BROKEN；修框后需新碎片再开）", center);
            }
        } finally {
            cleaning = false;
        }
    }
}
