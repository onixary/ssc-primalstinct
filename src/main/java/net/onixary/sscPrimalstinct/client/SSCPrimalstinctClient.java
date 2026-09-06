package net.onixary.sscPrimalstinct.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.client.ui.DebugHudPlaceholder;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;
import net.onixary.sscPrimalstinct.selection.SelectionPackets;

import java.util.ArrayList;
import java.util.List;

public class SSCPrimalstinctClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PrimalstinctStateS2C.ID, (client, handler, buf, responseSender) -> {
            PrimalstinctStateS2C payload = PrimalstinctStateS2C.read(buf);
            client.execute(() -> {
                long tick = client.player == null ? 0L : client.player.age;
                ClientPrimalstinctState.accept(payload, tick);
            });
        });

        DebugHudPlaceholder.register();
        CurlSleepKeybinding.register();

        // 卡13：选择协议接收器
        ClientPlayNetworking.registerGlobalReceiver(SelectionPackets.LIST_S2C, (client, handler, buf, rs) -> {
            int size = buf.readVarInt();
            List<Identifier> forms = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                forms.add(buf.readIdentifier());
            }
            int revision = buf.readVarInt();
            long nonce = buf.readVarLong();
            Identifier defaultForm = buf.readIdentifier();
            client.execute(() -> net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.accept(
                    new net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.SelectionList(
                            forms, revision, nonce, defaultForm)));
        });
        ClientPlayNetworking.registerGlobalReceiver(SelectionPackets.CONFIRMED_S2C, (client, handler, buf, rs) -> {
            client.execute(() -> {
                net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.onConfirmed();
                if (client.currentScreen instanceof net.onixary.sscPrimalstinct.client.selection.FormSelectionScreen) {
                    client.setScreen(null);
                }
            });
        });

        // tick：蜷缩键 + pending 时打开选择界面
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CurlSleepKeybinding.tick();
            var sel = net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.current();
            if (sel != null && !net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.isConfirmed()
                    && client.currentScreen == null) {
                client.setScreen(new net.onixary.sscPrimalstinct.client.selection.FormSelectionScreen(sel));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPrimalstinctState.clear();
            net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.clear();
        });

        SSCPrimalstinct.LOGGER.info("SSC Primalstinct client initialized");
    }
}
