package net.onixary.sscPrimalstinct.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 满值锁定演出 S2C：通知客户端开始驱动 SSC 变形屏幕叠加层（仅视觉效果，
 * 不经 SSC 的 transformState 包，不触发变形动画）。时长由服务端按 SSC
 * TRANSFORM_FX_DURATION_IN/OUT 计算并附带，客户端照此驱动强度曲线。
 */
public record LockCinematicS2C(int totalTicks) {

    public static final Identifier ID = Identifier.of(SSCPrimalstinct.MOD_ID, "lock_cinematic_s2c");

    public static LockCinematicS2C read(PacketByteBuf buf) {
        return new LockCinematicS2C(buf.readVarInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(totalTicks);
    }

    public static void send(ServerPlayerEntity player, int totalTicks) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        new LockCinematicS2C(totalTicks).write(buf);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
