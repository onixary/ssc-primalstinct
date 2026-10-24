package net.onixary.sscPrimalstinct.endgame.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import net.onixary.sscPrimalstinct.endgame.state.EndgamePlayerComponent;
import net.onixary.sscPrimalstinct.endgame.state.RegEndgameComponent;
import net.onixary.sscPrimalstinct.endgame.worldgen.AvatarSanctum;
import net.onixary.sscPrimalstinct.instinct.WanderAiController;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;
import net.onixary.sscPrimalstinct.sleep.CurlSleepController;
import net.onixary.sscPrimalstinct.util.AvatarDimension;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 祷属实现10：跨维度传送、个人返回锚点与多人隔离。
 * 统一校验（在线存活/非旁观/无过门冷却/目的维度可用/场景 ready）后由服务端门碰撞触发；
 * 只接受门面碰撞判定，不接收客户端目标坐标。
 * 锚点规则：仅“外部→化身维度”时写 returnAnchor（入口旁安全落点）；
 * 维度内走动、返程门碰撞不覆盖。返程目标动态读取接触者自身锚点——A 从祭坛甲进入、
 * B 从乙进入，同一返程门也各回甲乙（锚点属于玩家个体，禁止全局 lastPortal）。
 * 死亡复制锚点（CCA ALWAYS_COPY）；无有效返回落点时回玩家重生点，重生点失效则回主世界出生点。
 */
public final class EndgameTeleportService {

    /** 过门冷却（tick）；传送两端都设置，站在门内不连发。 */
    private static final long PORTAL_COOLDOWN_TICKS = 60L;
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private record PortalContact(ServerWorld world, BlockPos pos) {}
    private static final Map<UUID, PortalContact> PENDING = new HashMap<>();

    private EndgameTeleportService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EndgameTeleportService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING.clear();
            COOLDOWN.clear();
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING.remove(handler.player.getUuid());
            COOLDOWN.remove(handler.player.getUuid());
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    var player = handler.player;
                    if (player.getWorld().getRegistryKey() == AvatarDimension.WORLD_KEY
                            && AvatarSanctum.ensureReady(server)
                            && AvatarSanctum.isInsideLegacyPlaceholder(player.getBlockPos())) {
                        teleport(player, player.getServerWorld(), AvatarSanctum.SPAWN, AvatarSanctum.SPAWN_YAW, 0);
                    }
                }));
    }

    /** 门面碰撞入口（PrimalPortalBlock.onEntityCollision 调用）。 */
    public static void onPortalContact(ServerPlayerEntity player, ServerWorld world, BlockPos portalPos) {
        if (player.getServerWorld() != world || !player.isAlive() || player.isSpectator()) {
            return;
        }
        // Collision callbacks run inside Entity.move / movement packet validation.
        // Teleporting here lets the unfinished move overwrite the destination coordinates.
        PENDING.putIfAbsent(player.getUuid(), new PortalContact(world, portalPos.toImmutable()));
    }

    private static void processPortalContact(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.getWorld().getTime();
        Long last = COOLDOWN.get(player.getUuid());
        if (last != null && now - last < PORTAL_COOLDOWN_TICKS) {
            return;
        }
        if (COOLDOWN.size() > 64) {
            COOLDOWN.values().removeIf(t -> now - t > 200);
        }
        if (player.hasVehicle()) {
            player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.dismount"), true);
            return;
        }
        if (player.getWorld().getRegistryKey() == AvatarDimension.WORLD_KEY) {
            tryReturn(player);
        } else {
            tryEnter(player);
        }
    }

    // ---------- 进入（外部 → 化身维度） ----------

    private static void tryEnter(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerWorld target = AvatarDimension.world(server);
        if (target == null || !AvatarSanctum.ensureReady(server)) {
            // 维度缺失/场景初始化失败：服务端已有 error 日志，静默不传送
            return;
        }
        // 写入个人返回锚点（仅此路径写；入口旁安全落点，避开门面本身）
        Vec3d landing = findSafeLandingNear(player.getServerWorld(), player.getBlockPos());
        // 当前无落点时仍保存入口位置；返程重新检查，仍不可用则回重生点。
        BlockPos anchorPos = landing == null ? player.getBlockPos() : BlockPos.ofFloored(landing);
        EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
        component.setReturnAnchor(new EndgamePlayerComponent.ReturnAnchor(
                player.getWorld().getRegistryKey().getValue().toString(),
                anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(), player.getYaw(), player.getPitch()));
        teleport(player, target, AvatarSanctum.SPAWN, AvatarSanctum.SPAWN_YAW, 0.0f);
    }

    // ---------- 返回（化身维度 → 个人锚点） ----------

    private static void tryReturn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        EndgamePlayerComponent.ReturnAnchor anchor = RegEndgameComponent.ENDGAME.get(player).getReturnAnchor();
        ServerWorld destination = null;
        Vec3d pos = null;
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        if (anchor != null) {
            Identifier dimension = Identifier.tryParse(anchor.dimension());
            ServerWorld world = dimension == null ? null : server.getWorld(net.minecraft.registry.RegistryKey.of(
                    net.minecraft.registry.RegistryKeys.WORLD, dimension));
            if (world != null) {
                BlockPos anchorPos = new BlockPos(anchor.x(), anchor.y(), anchor.z());
                // 返回前复验落点：被堵则在附近另找，不要求门还存在
                pos = findSafeLandingNear(world, anchorPos);
                destination = world;
                yaw = anchor.yaw();
                pitch = anchor.pitch();
            }
        }
        if (destination == null || pos == null) {
            teleportToRespawn(player);
            player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.return_fallback").formatted(Formatting.RED), false);
            return;
        }
        teleport(player, destination, pos, yaw, pitch);
    }

    // ---------- 公共传送出口 ----------

    private static void teleport(ServerPlayerEntity player, ServerWorld target, BlockPos pos, float yaw, float pitch) {
        teleport(player, target, Vec3d.ofBottomCenter(pos), yaw, pitch);
    }

    private static void teleport(ServerPlayerEntity player, ServerWorld target, Vec3d pos, float yaw, float pitch) {
        // 停止游荡 AI 与蜷睡等主动控制，避免传送后旧世界控制器拉扯
        WanderAiController.stopPlayer(player);
        CurlSleepController.wakeUp(player, "endgame_teleport");
        player.teleport(target, pos.x, pos.y, pos.z, yaw, pitch);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        COOLDOWN.put(player.getUuid(), target.getTime());
        // 到达后同步本能状态快照（客户端外推/显隐立即校正）
        PrimalstinctNetwork.syncNow(player);
    }

    // ---------- 安全落点 ----------

    /** 保留原版落点的小数高度（台阶等），无可用位置时明确返回 null。 */
    private static @Nullable Vec3d findSafeLandingNear(ServerWorld world, BlockPos near) {
        for (BlockPos pos : BlockPos.iterateOutwards(near, 8, 3, 8)) {
            Vec3d landing = safeLanding(world, pos);
            if (landing != null) {
                return landing;
            }
        }
        return null;
    }

    private static @Nullable Vec3d safeLanding(ServerWorld world, BlockPos pos) {
        if (world.isOutOfHeightLimit(pos) || world.isOutOfHeightLimit(pos.up())) return null;
        // 原版处理实体支撑、碰撞形状和世界边界；使用严格的危险方块检查。
        Vec3d landing = Dismounting.findRespawnPos(EntityType.PLAYER, world, pos, true);
        if (landing == null) return null;
        Box bounds = EntityType.PLAYER.getDimensions().getBoxAt(landing);
        // 同时检查脚下及身体占用格，拒绝流体、危险方块和会立即再次传送的门面。
        for (BlockPos occupied : BlockPos.iterate(
                BlockPos.ofFloored(bounds.minX, bounds.minY - 0.01, bounds.minZ),
                BlockPos.ofFloored(bounds.maxX, bounds.maxY - 0.01, bounds.maxZ))) {
            BlockState state = world.getBlockState(occupied);
            if (!state.getFluidState().isEmpty() || EntityType.PLAYER.isInvalidSpawn(state)
                    || state.isOf(RegEndgameBlocks.PRIMAL_PORTAL) || state.isIn(BlockTags.PORTALS)) {
                return null;
            }
        }
        return landing;
    }

    private static void teleportToRespawn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerWorld spawnWorld = server.getWorld(player.getSpawnPointDimension());
        BlockPos spawn = player.getSpawnPointPosition();
        if (spawnWorld != null && spawn != null) {
            // alive=true：返程保留玩家实体与状态，也不消耗重生锚充能。
            var landing = PlayerEntity.findRespawnPosition(spawnWorld, spawn, player.getSpawnAngle(),
                    player.isSpawnForced(), true);
            if (landing.isPresent()) {
                teleport(player, spawnWorld, landing.get(), player.getSpawnAngle(), 0);
                return;
            }
        }
        // 床/重生锚已失效或未设置：回主世界出生区域，使用原版地表选点与向上避碰兜底。
        ServerWorld overworld = server.getOverworld();
        BlockPos worldSpawn = overworld.getSpawnPos();
        BlockPos surface = SpawnLocating.findServerSpawnPoint(overworld, new ChunkPos(worldSpawn));
        BlockPos reference = surface == null ? worldSpawn : surface;
        Vec3d landing = findSafeLandingNear(overworld, reference);
        if (landing == null) landing = Vec3d.ofBottomCenter(reference);
        Box bounds = player.getBoundingBox().offset(landing.subtract(player.getPos()));
        while (!overworld.isSpaceEmpty(player, bounds) && landing.y < overworld.getTopY()) {
            landing = landing.add(0, 1, 0);
            bounds = bounds.offset(0, 1, 0);
        }
        teleport(player, overworld, landing, overworld.getSpawnAngle(), 0);
    }

    // ---------- 维度内守卫：跌出场景安全边界回固定出生点 ----------

    private static void tick(MinecraftServer server) {
        var contacts = new HashMap<>(PENDING);
        PENDING.clear();
        contacts.forEach((uuid, contact) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && player.getServerWorld() == contact.world()
                    && player.getBoundingBox().intersects(new Box(contact.pos()).expand(0.5))
                    && contact.world().getBlockState(contact.pos()).isOf(RegEndgameBlocks.PRIMAL_PORTAL)) {
                processPortalContact(player);
            }
        });
        ServerWorld avatar = AvatarDimension.world(server);
        if (avatar == null) {
            return;
        }
        for (ServerPlayerEntity player : avatar.getPlayers()) {
            if (player.getY() < -8.0) {
                teleport(player, avatar, AvatarSanctum.SPAWN, AvatarSanctum.SPAWN_YAW, 0.0f);
                player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.fell").formatted(Formatting.RED), false);
            }
        }
    }
}
