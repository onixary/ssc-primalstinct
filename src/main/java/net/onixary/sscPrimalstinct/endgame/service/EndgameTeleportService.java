package net.onixary.sscPrimalstinct.endgame.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
 * 死亡复制锚点（CCA ALWAYS_COPY）；未过门而被命令送入者无锚点 → 主世界出生点兜底。
 */
public final class EndgameTeleportService {

    /** 过门冷却（tick）；传送两端都设置，站在门内不连发。 */
    private static final long PORTAL_COOLDOWN_TICKS = 60L;
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private EndgameTeleportService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EndgameTeleportService::tick);
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
    public static void onPortalContact(ServerPlayerEntity player) {
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
            player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.not_ready"), true);
            return;
        }
        // 写入个人返回锚点（仅此路径写；入口旁安全落点，避开门面本身）
        BlockPos landing = findSafeLandingNear(player.getServerWorld(), player.getBlockPos());
        EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
        component.setReturnAnchor(new EndgamePlayerComponent.ReturnAnchor(
                player.getWorld().getRegistryKey().getValue().toString(),
                landing.getX(), landing.getY(), landing.getZ(), player.getYaw(), player.getPitch()));
        teleport(player, target, AvatarSanctum.SPAWN, AvatarSanctum.SPAWN_YAW, 0.0f);
        player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.entered"), false);
    }

    // ---------- 返回（化身维度 → 个人锚点） ----------

    private static void tryReturn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        EndgamePlayerComponent.ReturnAnchor anchor = RegEndgameComponent.ENDGAME.get(player).getReturnAnchor();
        ServerWorld destination = null;
        BlockPos pos = null;
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        if (anchor != null) {
            var key = net.minecraft.registry.RegistryKey.of(
                    net.minecraft.registry.RegistryKeys.WORLD, Identifier.tryParse(anchor.dimension()));
            ServerWorld world = server.getWorld(key);
            if (world != null) {
                BlockPos anchorPos = new BlockPos(anchor.x(), anchor.y(), anchor.z());
                // 返回前复验落点：被堵则在附近另找，不要求门还存在
                pos = isSafeLanding(world, anchorPos) ? anchorPos : findSafeLandingNear(world, anchorPos);
                destination = world;
                yaw = anchor.yaw();
                pitch = anchor.pitch();
            }
        }
        if (destination == null || pos == null) {
            // 兜底：主世界出生点（未过门而被命令送入者也不被困住）
            destination = server.getOverworld();
            pos = destination.getSpawnPos();
            player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.return_fallback"), false);
        }
        teleport(player, destination, pos, yaw, pitch);
        player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.returned"), false);
    }

    // ---------- 公共传送出口 ----------

    private static void teleport(ServerPlayerEntity player, ServerWorld target, BlockPos pos, float yaw, float pitch) {
        // 停止游荡 AI 与蜷睡等主动控制，避免传送后旧世界控制器拉扯
        WanderAiController.stopPlayer(player);
        CurlSleepController.wakeUp(player, "endgame_teleport");
        player.teleport(target, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
        COOLDOWN.put(player.getUuid(), target.getTime());
        // 到达后同步本能状态快照（客户端外推/显隐立即校正）
        PrimalstinctNetwork.syncNow(player);
    }

    // ---------- 安全落点 ----------

    /** 从参考点向外找一个可站立格（下方实体方块非门面、本格与上方两格可通过）。 */
    private static BlockPos findSafeLandingNear(ServerWorld world, BlockPos near) {
        for (BlockPos pos : BlockPos.iterateOutwards(near, 8, 3, 8)) {
            if (isSafeLanding(world, pos)) {
                return pos.toImmutable();
            }
        }
        return near;  // 兜底：原点（teleport 自带的世界加载与推挤会处理极端情况）
    }

    private static boolean isSafeLanding(ServerWorld world, BlockPos pos) {
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());
        BlockState below = world.getBlockState(pos.down());
        return feet.isAir() && head.isAir()
                && !below.isAir() && !below.isOf(RegEndgameBlocks.PRIMAL_PORTAL)
                && !feet.isOf(RegEndgameBlocks.PRIMAL_PORTAL);
    }

    // ---------- 维度内守卫：跌出场景安全边界回固定出生点 ----------

    private static void tick(MinecraftServer server) {
        ServerWorld avatar = AvatarDimension.world(server);
        if (avatar == null) {
            return;
        }
        for (ServerPlayerEntity player : avatar.getPlayers()) {
            if (player.getY() < -8.0) {
                teleport(player, avatar, AvatarSanctum.SPAWN, AvatarSanctum.SPAWN_YAW, 0.0f);
                player.sendMessage(Text.translatable("ssc-primalstinct.endgame.portal.fell"), false);
            }
        }
    }
}
