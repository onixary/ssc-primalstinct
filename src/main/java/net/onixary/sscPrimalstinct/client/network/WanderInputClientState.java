package net.onixary.sscPrimalstinct.client.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.onixary.sscPrimalstinct.network.WanderManualInputC2S;

/** 仅上报手动输入以重置 AFK/释放接管，不修改玩家视角。 */
@Environment(EnvType.CLIENT)
public final class WanderInputClientState {
    private static final int REPORT_COOLDOWN_TICKS = 2;
    private static int reportCooldown;

    private WanderInputClientState() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WanderInputClientState::tick);
    }

    public static void clear() {
        reportCooldown = 0;
        WanderMotionClientState.clear();
    }

    public static boolean hasMovementInput(MinecraftClient client) {
        return client.options.forwardKey.isPressed() || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed() || client.options.rightKey.isPressed()
                || client.options.jumpKey.isPressed() || client.options.sneakKey.isPressed()
                || client.options.attackKey.isPressed() || client.options.useKey.isPressed();
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            clear();
            return;
        }
        if (reportCooldown > 0) {
            reportCooldown--;
        }
        boolean keyPressed = hasMovementInput(client) || player.input.pressingForward || player.input.pressingBack
                || player.input.pressingLeft || player.input.pressingRight
                || player.input.jumping || player.input.sneaking
                || client.options.attackKey.isPressed() || client.options.useKey.isPressed();
        if (keyPressed && reportCooldown == 0
                && ClientPlayNetworking.canSend(WanderManualInputC2S.ID)) {
            WanderMotionClientState.manualInput();
            reportCooldown = REPORT_COOLDOWN_TICKS;
            ClientPlayNetworking.send(WanderManualInputC2S.ID, new PacketByteBuf(Unpooled.buffer()));
        }
    }
}
