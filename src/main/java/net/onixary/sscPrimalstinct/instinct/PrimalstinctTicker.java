package net.onixary.sscPrimalstinct.instinct;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡04/05：附属服务端 tick 入口，内核见 PrimalstinctService.tick。
 * 顺序：基础增长→采样合并→应用持续变化→边界→（卡06 能力差量）→同步。
 */
public final class PrimalstinctTicker {

    private PrimalstinctTicker() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PrimalstinctTicker::tick);
        SSCPrimalstinct.LOGGER.debug("[primalstinct] server ticker registered");
    }

    private static void tick(MinecraftServer server) {
        PrimalstinctService.tick(server);
    }
}
