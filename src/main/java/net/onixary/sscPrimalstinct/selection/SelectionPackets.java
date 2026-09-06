package net.onixary.sscPrimalstinct.selection;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

import java.util.List;

/**
 * 卡12：选择协议。S2C 名单/确认回执；C2S confirm 只带 FormID+revision+nonce。
 */
public final class SelectionPackets {

    public static final Identifier LIST_S2C = Identifier.of(SSCPrimalstinct.MOD_ID, "selection_list");
    public static final Identifier CONFIRM_C2S = Identifier.of(SSCPrimalstinct.MOD_ID, "selection_confirm");
    public static final Identifier CONFIRMED_S2C = Identifier.of(SSCPrimalstinct.MOD_ID, "selection_confirmed");

    private SelectionPackets() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CONFIRM_C2S, (server, player, handler, buf, sender) -> {
            Identifier formId = Identifier.tryParse(buf.readString());
            int revision = buf.readVarInt();
            long nonce = buf.readVarLong();
            server.execute(() -> SelectionSessionManager.confirm(player, formId, revision, nonce));
        });
    }

    public static void sendSelectionList(ServerPlayerEntity player, List<Identifier> forms,
                                          int revision, long nonce, Identifier defaultForm) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(forms.size());
        for (Identifier id : forms) {
            buf.writeIdentifier(id);
        }
        buf.writeVarInt(revision);
        buf.writeVarLong(nonce);
        buf.writeIdentifier(defaultForm);
        ServerPlayNetworking.send(player, LIST_S2C, buf);
    }

    public static void sendSelectionConfirmed(ServerPlayerEntity player, Identifier formId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeIdentifier(formId);
        ServerPlayNetworking.send(player, CONFIRMED_S2C, buf);
    }
}
