package net.onixary.sscPrimalstinct.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡03/09：S2C 状态快照。value/rate/level/locked/serverTick/revision + 库存锁槽规则
 * （allowedHotbar/allowedMain，客户端渲染紧凑快捷栏与深灰占位槽用）。
 * 1.20.1 网络 API：Identifier + PacketByteBuf（与 SSC 的 ModPackets 同风格）。
 */
public record PrimalstinctStateS2C(
        float value,
        float rate,
        int level,
        boolean locked,
        long serverTick,
        int revision,
        int allowedHotbar,
        int allowedMain) {

    public static final Identifier ID = Identifier.of(SSCPrimalstinct.MOD_ID, "state_s2c");

    public static PrimalstinctStateS2C read(PacketByteBuf buf) {
        return new PrimalstinctStateS2C(
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    public void write(PacketByteBuf buf) {
        buf.writeFloat(value);
        buf.writeFloat(rate);
        buf.writeVarInt(level);
        buf.writeBoolean(locked);
        buf.writeVarLong(serverTick);
        buf.writeVarInt(revision);
        buf.writeVarInt(allowedHotbar);
        buf.writeVarInt(allowedMain);
    }
}
