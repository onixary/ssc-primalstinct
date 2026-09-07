package net.onixary.sscPrimalstinct.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/** Horizontal AI intent and optional one-shot jump; never streams falling velocity. */
public record WanderMotionS2C(boolean active, double x, double z, double jump) {
    public static final Identifier ID = Identifier.of(SSCPrimalstinct.MOD_ID, "wander_motion");

    public static WanderMotionS2C read(PacketByteBuf buf) {
        return new WanderMotionS2C(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void send(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(active);
        buf.writeDouble(x);
        buf.writeDouble(z);
        buf.writeDouble(jump);
        ServerPlayNetworking.send(player, ID, buf);
    }

    public static void clear(ServerPlayerEntity player) {
        new WanderMotionS2C(false, 0, 0, Double.NaN).send(player);
    }
}
