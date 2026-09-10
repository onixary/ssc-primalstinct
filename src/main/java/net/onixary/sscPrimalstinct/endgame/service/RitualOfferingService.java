package net.onixary.sscPrimalstinct.endgame.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlockEntity;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPedestalBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 眷属实现05：投掷献祭检测与物品消耗事务。
 * 由已加载的未完成基座 BE 每 5 tick 定时查询小 AABB（按坐标哈希错峰），
 * 扫描空间只覆盖基座顶面接收区（2..14/16 水平范围，台面上方一层）。
 *
 * <p>投掷者判定：原版 1.20.1 服务端丢弃路径（ServerPlayerEntity.dropSelectedItem →
 * dropItem(stack, false, retainOwnership=true)）稳定写入 Thrower NBT，ItemEntity.getOwner()
 * 在服务端解析为该实体；漏斗/发射器/怪物掉落无 Thrower → 不献祭（已核实 yarn 源码）。
 * 混合来源物品堆：合并实体保留首个实体的 Thrower，非投掷者来源的贡献随之失去资格
 * （白板给出的两种策略中的后一种，避免低级玩家借满级来源绕过）。
 *
 * <p>消费事务：同一段服务端逻辑中重读 stack 与 fulfilled——物品仍存在、ID/数量符合、
 * 投掷者在线同世界且满足最高级、基座未完成、实例绑定有效；整组 64 个只扣所需数量，
 * 剩余保留。错误物品原样保留；已完成基座立即退出扫描，不吞第二件。</p>
 */
public final class RitualOfferingService {

    /** 扫描周期（tick）；按坐标哈希把各基座分摊到周期窗口内。 */
    private static final int SCAN_INTERVAL_TICKS = 5;

    private static final Map<BlockPos, PrimalPedestalBlockEntity> PENDING = new ConcurrentHashMap<>();

    private RitualOfferingService() {
    }

    public static void register() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof PrimalPedestalBlockEntity pedestal && !pedestal.isFulfilled()) {
                PENDING.put(pedestal.getPos(), pedestal);
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof PrimalPedestalBlockEntity pedestal) {
                PENDING.remove(pedestal.getPos());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(RitualOfferingService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long time = server.getTicks();
        for (PrimalPedestalBlockEntity pedestal : List.copyOf(PENDING.values())) {
            if (pedestal.isRemoved() || pedestal.isFulfilled()
                    || !(pedestal.getWorld() instanceof ServerWorld world)) {
                PENDING.remove(pedestal.getPos());
                continue;
            }
            // 错峰：按坐标哈希分摊到扫描窗口
            if (((pedestal.getPos().hashCode() & 0x7fffffff) + time) % SCAN_INTERVAL_TICKS != 0) {
                continue;
            }
            try {
                scanPedestal(world, pedestal);
            } catch (Throwable t) {
                SSCPrimalstinct.LOGGER.error("[primalstinct] 基座 {} 献祭扫描异常", pedestal.getPos(), t);
            }
        }
    }

    private static void scanPedestal(ServerWorld world, PrimalPedestalBlockEntity pedestal) {
        // 未绑定实例（手工放置/配置缺供物）不献祭
        Identifier requirementId = pedestal.getRequirementItem();
        if (requirementId == null || pedestal.getRitualId() == null) {
            return;
        }
        if (!Registries.ITEM.containsId(requirementId)) {
            return;
        }
        int need = pedestal.getRequirementCount();
        // 接收区：基座整格上方的台面以上一层（台面 10/16 高；y 下沿 0.55 排除落地旁边的物品，
        // 上沿 1.8 覆盖轻微弹跳；水平放宽到整格，物品停在台面边缘也能命中）
        BlockPos pos = pedestal.getPos();
        Box zone = new Box(
                pos.getX(), pos.getY() + 0.55, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.8, pos.getZ() + 1.0);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, zone, ItemEntity::isAlive)) {
            if (consumeIfValid(world, pedestal, item, requirementId, need)) {
                return;  // 已完成，立即退出扫描（不吞第二件）
            }
        }
    }

    /** 服务端单段消费事务；返回是否完成献祭。 */
    private static boolean consumeIfValid(ServerWorld world, PrimalPedestalBlockEntity pedestal,
                                          ItemEntity item, Identifier requirementId, int need) {
        ItemStack stack = item.getStack();
        if (stack.getCount() < need || !requirementId.equals(Registries.ITEM.getId(stack.getItem()))) {
            return false;  // 错误物品原样保留
        }
        // 投掷者：无玩家来源（漏斗/发射器/怪物掉落）不献祭
        if (!(item.getOwner() instanceof ServerPlayerEntity thrower)) {
            return false;
        }
        if (thrower.getWorld() != world || !thrower.isAlive()) {
            return false;  // 离线/异世界来源
        }
        // 消费时再核验资格（投掷后降级在此拒绝）；给出可感知反馈（限频），避免静默失败难排查
        if (!EndgameEligibility.canSacrifice(thrower)) {
            feedbackRejection(thrower, item);
            return false;
        }
        // 消费重读守卫（同一实体可能被上一轮处理过）
        if (pedestal.isFulfilled() || pedestal.isRemoved()) {
            return true;
        }
        // 扣需求数量，剩余保留（64 只扣 1，余 63 留在原地）
        stack.decrement(need);
        item.setStack(stack);
        pedestal.markFulfilled();
        // 表现：粒子 + 音效 + 献祭者反馈
        net.minecraft.util.math.Vec3d center = net.minecraft.util.math.Vec3d.ofCenter(pedestal.getPos()).add(0, 0.3, 0);
        world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                12, 0.2, 0.3, 0.2, 0.02);
        world.playSound(null, pedestal.getPos(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 0.8f);
        thrower.sendMessage(Text.translatable("ssc-primalstinct.endgame.pedestal.accepted"), true);
        // 祭坛重算三路供能（邻块变化即时重算路径）
        BlockPos controller = pedestal.getControllerPos();
        if (controller != null && world.getBlockEntity(controller) instanceof PrimalAltarBlockEntity altar) {
            altar.recomputeActive();
        }
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 完成基座 {} 的献祭（ritual={}，物品={} x{}）",
                thrower.getGameProfile().getName(), pedestal.getPos(), pedestal.getRitualId(),
                requirementId, need);
        return true;
    }

    /** 供断线/卸载后的登记校正（BE 重新加载时由事件重新入列）。 */
    public static void forget(@Nullable BlockPos pos) {
        if (pos != null) {
            PENDING.remove(pos);
        }
    }

    // ---------- 拒绝反馈（限频：同一物品实体 3s 一条） ----------

    private static final java.util.Map<Integer, Long> REJECT_FEEDBACK = new java.util.HashMap<>();

    private static void feedbackRejection(ServerPlayerEntity thrower, ItemEntity item) {
        long now = thrower.getWorld().getTime();
        Long last = REJECT_FEEDBACK.get(item.getId());
        if (last != null && now - last < 60) {
            return;
        }
        REJECT_FEEDBACK.put(item.getId(), now);
        REJECT_FEEDBACK.values().removeIf(t -> now - t > 200);  // 顺带清理
        thrower.sendMessage(Text.translatable("ssc-primalstinct.endgame.pedestal.rejected"), true);
    }

    // ---------- 诊断（debug 命令用） ----------

    /** 报告指定基座的接收区实时状态与登记情况。 */
    public static String describe(ServerWorld world, PrimalPedestalBlockEntity pedestal) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("pending=%s fulfilled=%s ritual=%s requirement=%s x%d controller=%s",
                PENDING.containsKey(pedestal.getPos()), pedestal.isFulfilled(),
                pedestal.getRitualId(), pedestal.getRequirementItem(),
                pedestal.getRequirementCount(), pedestal.getControllerPos()));
        BlockPos pos = pedestal.getPos();
        Box zone = new Box(pos.getX(), pos.getY() + 0.55, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.8, pos.getZ() + 1.0);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, zone, ItemEntity::isAlive);
        sb.append(" | zoneItems=").append(items.size());
        for (ItemEntity item : items) {
            var owner = item.getOwner();
            boolean eligible = owner instanceof ServerPlayerEntity p && EndgameEligibility.canSacrifice(p);
            sb.append(String.format("\n  - %s x%d owner=%s eligible=%s",
                    item.getStack().getItem(), item.getStack().getCount(),
                    owner == null ? "(none)" : owner.getName().getString(), eligible));
        }
        return sb.toString();
    }
}
