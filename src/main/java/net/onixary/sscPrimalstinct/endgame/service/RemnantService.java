package net.onixary.sscPrimalstinct.endgame.service;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.structure.Structure;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRitualConfig;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRosterManager;
import net.onixary.sscPrimalstinct.endgame.entity.PrimalRemnantEntity;
import net.onixary.sscPrimalstinct.endgame.state.EndgameWorldState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 眷属实现07：原初残余投放与结构指引（唯一服务入口）。
 * 等级召唤提示不再单独发聊天（2026-09-14 用户决策：升级 actionbar 文案已承担升阶提示）。
 *
 * <h3>残余投放</h3>
 * 两条入口共用本类的 launch：击杀触发（SpawnPrimalRemnantAction，先做服务端资格核验并
 * 按数据包概率只掷一次随机）与手动使用（PrimalRemnantItem，失败不消耗）。
 *
 * <h3>结构指引</h3>
 * 用结构 ID 查最近原初祭坛（借鉴 EnderEyeItem 的 locateStructure 用法）：
 * 限搜索半径与重试次数；缓存维度/结构坐标（玩家近距离移动直接复用，负结果短 TTL）；
 * 最近遗迹已 SPENT 时沿方向有限次推进寻找下一座，全部耗尽提示向远处探索；
 * 无结构/不支持维度返回“此处没有回应”（限频）。全部在服务端主线程执行，
 * 不在工作线程访问 ServerWorld。
 */
public final class RemnantService {

    /** 结构搜索半径（chunk；与原版末影之眼一致的量级）。 */
    public static final int SEARCH_RADIUS_CHUNKS = 100;
    /** 每玩家同时在途的残余实体上限（防连杀刷屏）。 */
    public static final int MAX_IN_FLIGHT = 3;
    /** SPENT 跳过的最大定位次数（含首次；不能对整个世界无限扫描）。 */
    private static final int MAX_LOCATE_ATTEMPTS = 3;
    /** SPENT 后沿玩家→遗迹方向的推进距离（格）：略大于 structure_set spacing 90 chunk=1440 格。 */
    private static final double SPENT_PUSH_BLOCKS = 1500.0;
    /** 正结果缓存复用半径（格）：玩家在此范围内活动不重新定位。 */
    private static final double CACHE_REUSE_RADIUS = 128.0;
    /** 负结果（无目标/全耗尽）缓存有效期（tick）：连续杀怪不重复螺旋扫区块。 */
    private static final int NEGATIVE_CACHE_TICKS = 600;
    /** “此处没有回应”类提示的每玩家限频间隔（tick）。 */
    private static final int FEEDBACK_COOLDOWN_TICKS = 200;
    /** 在途实体统计半径（格）。 */
    private static final double IN_FLIGHT_SCAN_RADIUS = 64.0;
    /** 生成位置：玩家面前 1.5 格（击杀触发与手动使用统一，不依赖击杀目标位置）。 */
    private static final double SPAWN_FORWARD_BLOCKS = 1.5;

    private static final Map<UUID, TargetCache> TARGET_CACHE = new HashMap<>();
    private static final Map<UUID, Long> LAST_FEEDBACK = new HashMap<>();

    private RemnantService() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            TARGET_CACHE.remove(handler.player.getUuid());
            LAST_FEEDBACK.remove(handler.player.getUuid());
        });
    }

    // ---------- 残余投放 ----------

    public enum LaunchResult {
        LAUNCHED, NO_TARGET, RANGE_EXHAUSTED, CAPPED
    }

    /**
     * 击杀触发入口（SpawnPrimalRemnantAction 调用）：
     * 服务端资格核验（名单内形态 + 实际最高级）→ 按数据包概率只掷一次随机 → launch。
     * 低级/名单外/未掷中一律静默返回。
     */
    public static void tryLaunchFromKill(ServerPlayerEntity player) {
        if (!EndgameEligibility.atMaxLevel(player)) {
            if (SSCPrimalstinct.isDevelopmentEnvironment()) {
                SSCPrimalstinct.LOGGER.debug("[primalstinct] spawn_primal_remnant 资格核验未过（玩家 {}）",
                        player.getGameProfile().getName());
            }
            return;
        }
        EndgameRitualConfig config = EndgameRosterManager.active();
        float chance = config.remnantDropChance;
        if (chance <= 0.0f) {
            return;
        }
        if (chance < 1.0f && player.getRandom().nextFloat() >= chance) {
            return;  // 统一只掷一次随机：未掷中不再进入定位/投放
        }
        launch(player);
    }

    /**
     * 手动使用入口（PrimalRemnantItem 调用；创造取得 + 手动 use 走同一 launch 服务）。
     * 不掷概率；定位失败不消耗由调用方按返回值处理。
     */
    public static LaunchResult tryLaunchFromItem(ServerPlayerEntity player) {
        return launch(player);
    }

    private static LaunchResult launch(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return LaunchResult.NO_TARGET;
        }
        // 每玩家在途数量上限：连杀/连用不刷实体
        UUID owner = player.getUuid();
        int inFlight = world.getEntitiesByClass(PrimalRemnantEntity.class,
                player.getBoundingBox().expand(IN_FLIGHT_SCAN_RADIUS),
                remnant -> remnant.isAlive() && owner.equals(remnant.getOwnerUuid())).size();
        if (inFlight >= MAX_IN_FLIGHT) {
            return LaunchResult.CAPPED;
        }
        LocateResult target = locateTarget(world, player);
        switch (target.state()) {
            case NO_TARGET -> {
                feedback(player, "chat.ssc-primalstinct.remnant.no_response");
                return LaunchResult.NO_TARGET;
            }
            case RANGE_EXHAUSTED -> {
                feedback(player, "chat.ssc-primalstinct.remnant.range_exhausted");
                return LaunchResult.RANGE_EXHAUSTED;
            }
            default -> {
            }
        }
        BlockPos altarPos = target.pos();
        // 生成位置统一取玩家面前 1.5 格（用户决策 2026-09-13：不依赖击杀目标位置，
        // 不与 Apoli 注入点耦合）；带小散布的“爆出”视觉
        Vec3d spawnPos = player.getEyePos().add(player.getRotationVector().multiply(SPAWN_FORWARD_BLOCKS));
        double x = spawnPos.x + (world.random.nextDouble() - 0.5) * 0.4;
        double y = spawnPos.y;
        double z = spawnPos.z + (world.random.nextDouble() - 0.5) * 0.4;
        PrimalRemnantEntity remnant = new PrimalRemnantEntity(world, x, y, z, owner);
        remnant.setVelocity((world.random.nextDouble() - 0.5) * 0.2, 0.25, (world.random.nextDouble() - 0.5) * 0.2);
        remnant.initTargetPos(altarPos);
        world.emitGameEvent(GameEvent.PROJECTILE_SHOOT, remnant.getPos(), GameEvent.Emitter.of(player));
        world.spawnEntity(remnant);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDER_EYE_LAUNCH, SoundCategory.NEUTRAL,
                0.6F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F) * 0.75F);
        SSCPrimalstinct.LOGGER.debug("[primalstinct] 原初残余已投放（玩家 {} → 祭坛 {}）",
                player.getGameProfile().getName(), altarPos);
        return LaunchResult.LAUNCHED;
    }

    /** 失败提示限频：同玩家在冷却窗口内不重复发送。 */
    private static void feedback(ServerPlayerEntity player, String key) {
        long now = player.getWorld().getTime();
        Long last = LAST_FEEDBACK.get(player.getUuid());
        if (last != null && now - last < FEEDBACK_COOLDOWN_TICKS) {
            return;
        }
        LAST_FEEDBACK.put(player.getUuid(), now);
        player.sendMessage(Text.translatable(key).formatted(Formatting.RED), false);
    }

    // ---------- 结构定位 ----------

    /**
     * 定位缓存：FOUND 携带结构坐标（复用前需复核 SPENT）；
     * NO_TARGET / RANGE_EXHAUSTED 为负结果（仅短 TTL 内复用，避免连续击杀反复扫区块）。
     */
    private record TargetCache(String dimension, LocateState state, @Nullable BlockPos pos,
                               BlockPos searchCenter, long resolvedAt) {
    }

    private record LocateResult(LocateState state, @Nullable BlockPos pos) {
        static LocateResult of(LocateState state, @Nullable BlockPos pos) {
            return new LocateResult(state, pos);
        }
    }

    private enum LocateState {
        FOUND, NO_TARGET, RANGE_EXHAUSTED
    }

    /**
     * 最近祭坛定位：缓存复用 → SPENT 有限次跳过 → 负结果短 TTL 缓存。
     * 只在服务端主线程调用；locateStructure 只推进区块到 STRUCTURE_STARTS 阶段。
     */
    private static LocateResult locateTarget(ServerWorld world, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        String dimension = world.getRegistryKey().getValue().toString();
        BlockPos playerPos = player.getBlockPos();
        EndgameRitualConfig config = EndgameRosterManager.active();

        TargetCache cached = TARGET_CACHE.get(uuid);
        if (cached != null && cached.dimension().equals(dimension)
                && playerPos.isWithinDistance(cached.searchCenter(), CACHE_REUSE_RADIUS)) {
            boolean usable = cached.state() == LocateState.FOUND
                    ? !isSpent(world, cached.pos(), config)
                    : world.getTime() - cached.resolvedAt() < NEGATIVE_CACHE_TICKS;
            if (usable) {
                return LocateResult.of(cached.state(), cached.pos());
            }
            TARGET_CACHE.remove(uuid);  // 正结果已 SPENT / 负结果过期 → 重定位
        }

        BlockPos center = playerPos;
        boolean foundSpent = false;
        for (int attempt = 0; attempt < MAX_LOCATE_ATTEMPTS; attempt++) {
            BlockPos found = locateOnce(world, center, config.structureId);
            if (found == null) {
                LocateState state = foundSpent ? LocateState.RANGE_EXHAUSTED : LocateState.NO_TARGET;
                TARGET_CACHE.put(uuid, new TargetCache(dimension, state, null, playerPos, world.getTime()));
                return LocateResult.of(state, null);
            }
            if (!isSpent(world, found, config)) {
                TARGET_CACHE.put(uuid, new TargetCache(dimension, LocateState.FOUND, found, playerPos, world.getTime()));
                return LocateResult.of(LocateState.FOUND, found);
            }
            foundSpent = true;
            // 沿玩家→遗迹方向推进搜索中心，越过已耗尽遗迹找下一座（structure_set spacing 量级）
            double dx = found.getX() - center.getX();
            double dz = found.getZ() - center.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < 1.0) {
                break;
            }
            center = found.add((int) Math.round(dx / horizontal * SPENT_PUSH_BLOCKS), 0,
                    (int) Math.round(dz / horizontal * SPENT_PUSH_BLOCKS));
        }
        TARGET_CACHE.put(uuid, new TargetCache(dimension, LocateState.RANGE_EXHAUSTED, null,
                playerPos, world.getTime()));
        return LocateResult.of(LocateState.RANGE_EXHAUSTED, null);
    }

    /** 单次结构定位（结构 ID → 注册表条目 → ChunkGenerator 螺旋搜索）。 */
    private static @Nullable BlockPos locateOnce(ServerWorld world, BlockPos center,
                                                 net.minecraft.util.Identifier structureId) {
        Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        var entry = registry.getEntry(net.minecraft.registry.RegistryKey.of(RegistryKeys.STRUCTURE, structureId));
        if (entry.isEmpty()) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 结构 {} 不在注册表中，无法定位祭坛", structureId);
            return null;
        }
        Pair<BlockPos, net.minecraft.registry.entry.RegistryEntry<Structure>> result =
                world.getChunkManager().getChunkGenerator()
                        .locateStructure(world, net.minecraft.registry.entry.RegistryEntryList.of(entry.get()),
                                center, SEARCH_RADIUS_CHUNKS, false);
        return result != null ? result.getFirst() : null;
    }

    /** SPENT 判定：世界状态实例记录（不加载区块；奖励已领=该遗迹已耗尽）。 */
    private static boolean isSpent(ServerWorld world, BlockPos altarPos, EndgameRitualConfig config) {
        return EndgameWorldState.get(world).isRewardClaimed(world, altarPos, config.ritualId);
    }
}
