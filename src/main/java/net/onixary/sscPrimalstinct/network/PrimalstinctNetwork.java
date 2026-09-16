package net.onixary.sscPrimalstinct.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalRoster;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 卡03：同步协议（服务端侧）。S2C 载荷编解码见 PrimalstinctStateS2C（客户端接收器在 client 包）。
 * 触发：值被瞬间修改/跨级/锁定变化（syncNow）；登录/复活/换维（事件钩子）；
 * 持续增长时限频快照校正（tick，快照落后时补发；rate != 0 的增长场景由卡05 内核接入）。
 * 断线清理临时缓存；不以离线时长累计本能（无离线推进逻辑）。
 */
public final class PrimalstinctNetwork {

    /** 限频校正间隔（tick）；开发初值，卡05 接入真实速率后按需调整。 */
    private static final int CORRECTION_INTERVAL = 40;

    private record LastSnapshot(float value, int level, boolean locked, float rate, int revision, boolean managed) {
    }

    private static final Map<UUID, LastSnapshot> LAST_SENT = new HashMap<>();

    private PrimalstinctNetwork() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(handler.player);
                    component.repairInvariants();
                    syncNow(handler.player);
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LAST_SENT.remove(handler.player.getUuid());
            net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.forget(handler.player.getUuid());
            PrimalstinctService.clearPlayer(handler.player.getUuid());  // 运行期速率重登重算
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncNow(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> syncNow(player));
        // 卡15：形态切换后的 managed 即时同步由 SSCPrimalstinct 主初始化器的
        // FORM_CHANGE_END 钩子触发（SSC 事件类的 import 集中在主类，与既有约定一致）

        ServerTickEvents.END_SERVER_TICK.register(PrimalstinctNetwork::tickCorrection);
    }

    public static PrimalstinctComponent component(ServerPlayerEntity player) {
        return RegPrimalstinctComponent.PRIMALSTINCT.get(player);
    }

    /** 立即同步（瞬间修改/跨级/锁定变化/登录/复活/换维后调用）；速率为运行期合并值。 */
    public static void syncNow(ServerPlayerEntity player) {
        syncNow(player, PrimalstinctService.currentRate(player));
    }

    public static void syncNow(ServerPlayerEntity player, float rate) {
        PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
        PrimalstinctStateS2C payload = snapshot(player.getServer(), player, component, rate);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        payload.write(buf);
        ServerPlayNetworking.send(player, PrimalstinctStateS2C.ID, buf);
        LAST_SENT.put(player.getUuid(), new LastSnapshot(
                component.getValue(), component.getLevel(), component.isLocked(), rate, payload.revision(), payload.managed()));
    }

    private static PrimalstinctStateS2C snapshot(MinecraftServer server, ServerPlayerEntity player,
                                                 PrimalstinctComponent component, float rate) {
        net.onixary.sscPrimalstinct.inventory.InventoryLockRule rule =
                net.onixary.sscPrimalstinct.inventory.InventoryLockManager.rule(player);
        PrimalRoster roster = PrimalRosterManager.active();
        return new PrimalstinctStateS2C(
                component.getValue(),
                rate,
                component.getLevel(),
                component.isLocked(),
                server == null ? 0L : server.getTicks(),
                roster.revision,
                rule == null ? 9 : rule.allowedHotbarSlots(),
                rule == null ? 27 : rule.allowedMainSlots(),
                isManaged(player),
                roster.levels.maxValue,
                PrimalstinctService.BASE_GROWTH_PER_SECOND,
                roster.levels.thresholds,
                net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig.chooseFormOnStart(),
                net.onixary.sscPrimalstinct.selection.SelectionSessionManager.isPending(player),
                PrimalstinctService.pulseDirection(player));
    }

    /** 卡15：当前 SSC 形态是否受本玩法管理（名单内含子形态继承）。HUD 显隐以此为准，不读 NoInstinct。 */
    private static boolean isManaged(ServerPlayerEntity player) {
        return net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player);
    }

    private static void tickCorrection(MinecraftServer server) {
        if (server.getTicks() % CORRECTION_INTERVAL != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
            LastSnapshot last = LAST_SENT.get(player.getUuid());
            // 限频校正：仅在来源/速率/数值/等级/锁定变化时补发（卡07：减少逐 tick 同步）
            float currentRate = PrimalstinctService.currentRate(player);
            boolean changed = last == null
                    || last.value() != component.getValue()
                    || last.level() != component.getLevel()
                    || last.locked() != component.isLocked()
                    || last.rate() != currentRate
                    || last.revision() != PrimalRosterManager.active().revision
                    || last.managed() != isManaged(player);
            if (changed) {
                syncNow(player);
            }
        }
    }
}
