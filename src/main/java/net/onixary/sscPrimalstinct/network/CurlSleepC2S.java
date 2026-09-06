package net.onixary.sscPrimalstinct.network;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.sleep.CurlSleepController;

/**
 * 卡11：蜷缩切换请求（C2S）。客户端只请求，不自设 sleeping；服务端权威校验。
 */
public final class CurlSleepC2S {

    public static final Identifier ID = Identifier.of(SSCPrimalstinct.MOD_ID, "curl_sleep");

    private CurlSleepC2S() {
    }

    public static void registerServer() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, responseSender) -> server.execute(
                        () -> CurlSleepController.toggle(player)));
    }
}
