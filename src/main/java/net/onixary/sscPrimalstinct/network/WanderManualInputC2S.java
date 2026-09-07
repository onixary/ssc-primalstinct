package net.onixary.sscPrimalstinct.network;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.instinct.WanderAiController;

/**
 * 空闲及游荡期间的手动输入上报（C2S）：客户端检测到移动/跳跃/潜行/攻击/使用按键时上报，
 * 服务端重置 AFK 计时并释放控制权。
 */
public final class WanderManualInputC2S {

    public static final Identifier ID = Identifier.of(SSCPrimalstinct.MOD_ID, "wander_manual_input");

    private WanderManualInputC2S() {
    }

    public static void registerServer() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ID,
                (server, player, handler, buf, responseSender) -> server.execute(
                        () -> WanderAiController.markManualInput(player)));
    }
}
