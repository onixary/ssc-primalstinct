package net.onixary.sscPrimalstinct.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡03/09/15：S2C 状态快照。value/rate/level/locked/serverTick/revision + 库存锁槽规则
 * （allowedHotbar/allowedMain，客户端渲染紧凑快捷栏与深灰占位槽用）。
 * 卡15 增补显示元数据：managed（当前形态是否受本玩法管理，HUD 显隐规则）、
 * maxValue/baseRate/thresholds（等级表刻度与速率分档基准；多人环境下客户端无法读取
 * 服务端数据包，随快照同步）。baseRate 供 HUD 复刻旧 SSC 的“增速分档外框提示”：
 * rate 相对 baseRate 的超出量决定条外框/填充的分档表现。
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
        int allowedMain,
        boolean managed,
        float maxValue,
        float baseRate,
        float[] thresholds) {

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
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readFloat(),
                readThresholds(buf));
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
        buf.writeBoolean(managed);
        buf.writeFloat(maxValue);
        buf.writeFloat(baseRate);
        writeThresholds(buf, thresholds);
    }

    private static float[] readThresholds(PacketByteBuf buf) {
        int size = buf.readVarInt();
        float[] thresholds = new float[Math.max(0, size)];
        for (int i = 0; i < thresholds.length; i++) {
            thresholds[i] = buf.readFloat();
        }
        return thresholds;
    }

    private static void writeThresholds(PacketByteBuf buf, float[] thresholds) {
        buf.writeVarInt(thresholds.length);
        for (float threshold : thresholds) {
            buf.writeFloat(threshold);
        }
    }
}
