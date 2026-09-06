package net.onixary.sscPrimalstinct.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.client.effect.PrimalstinctWarningParticles;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.client.ui.PrimalInstinctHud;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;
import net.onixary.sscPrimalstinct.selection.SelectionPackets;

import java.util.ArrayList;
import java.util.List;

public class SSCPrimalstinctClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 卡15：客户端配置（位置锚点/偏移/状态文本开关），首次运行生成默认文件
        PrimalstinctClientConfig.get();

        ClientPlayNetworking.registerGlobalReceiver(PrimalstinctStateS2C.ID, (client, handler, buf, responseSender) -> {
            PrimalstinctStateS2C payload = PrimalstinctStateS2C.read(buf);
            client.execute(() -> ClientPrimalstinctState.accept(payload, client.player));
        });

        // 卡15：正式本能 HUD（替换卡03 的 DebugHudPlaceholder）+ 限频预警粒子
        PrimalInstinctHud.register();
        PrimalstinctWarningParticles.register();
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
            PrimalInstinctHud.clearHint();
        });

        SSCPrimalstinct.LOGGER.info("SSC Primalstinct client initialized");
    }
}
