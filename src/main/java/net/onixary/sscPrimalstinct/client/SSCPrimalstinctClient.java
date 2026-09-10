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
        net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.setClientManaged(
                player -> player == net.minecraft.client.MinecraftClient.getInstance().player && ClientPrimalstinctState.managed());
        // 卡15/17：客户端配置（AutoConfig 注册即读文件；ModMenu 保存后 HUD 实时生效）
        PrimalstinctClientConfig.register();
        net.onixary.sscPrimalstinct.client.network.PerceptionClientState.register();

        ClientPlayNetworking.registerGlobalReceiver(PrimalstinctStateS2C.ID, (client, handler, buf, responseSender) -> {
            PrimalstinctStateS2C payload = PrimalstinctStateS2C.read(buf);
            client.execute(() -> {
                ClientPrimalstinctState.accept(payload, client.player);
                if (!payload.selectionPending()) {
                    net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.clear();
                    if (client.currentScreen instanceof net.onixary.sscPrimalstinct.client.selection.FormSelectionScreen) client.setScreen(null);
                }
            });
        });

        // 游荡仅接管位移；客户端上报输入，不改写镜头。
        net.onixary.sscPrimalstinct.client.network.WanderInputClientState.register();
        net.onixary.sscPrimalstinct.client.network.WanderMotionClientState.register();

        // 满值锁定演出：客户端驱动 SSC 变形屏幕叠加层（服务端同时经 SSC noMove/noJump 包限制移动）
        ClientPlayNetworking.registerGlobalReceiver(
                net.onixary.sscPrimalstinct.network.LockCinematicS2C.ID, (client, handler, buf, rs) -> {
                    net.onixary.sscPrimalstinct.network.LockCinematicS2C payload =
                            net.onixary.sscPrimalstinct.network.LockCinematicS2C.read(buf);
                    client.execute(() -> net.onixary.sscPrimalstinct.client.effect.PrimalstinctLockCinematic.start(payload.totalTicks()));
                });
        net.onixary.sscPrimalstinct.client.effect.PrimalstinctLockCinematic.register();

        // 卡15：正式本能 HUD（替换卡03 的 DebugHudPlaceholder）+ 限频预警粒子
        PrimalInstinctHud.register();
        PrimalstinctWarningParticles.register();
        CurlSleepKeybinding.register();

        // 眷属实现03：终局方块实体渲染器（基座供物图标；专用服务器不加载）
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlockEntities.PRIMAL_PEDESTAL,
                net.onixary.sscPrimalstinct.endgame.client.PrimalPedestalRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlockEntities.PRIMAL_ALTAR,
                net.onixary.sscPrimalstinct.endgame.client.PrimalAltarRenderer::new);

        // 卡16：无书快捷访问（调色菜单 / 图鉴页面），默认不绑定；图鉴 INSTINCTS 列扩展注册（SSC 公开接口）
        BookAccessKeybindings.register();
        net.onixary.sscPrimalstinct.adapter.ssc.SSCClientAdapter.registerCodexColumnProvider(
                net.onixary.sscPrimalstinct.client.ui.PrimalstinctCodexColumnProvider.INSTANCE);

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
            client.execute(() -> {
                var list = new net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.SelectionList(
                        forms, revision, nonce, defaultForm);
                net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.accept(list);
                if (client.currentScreen instanceof net.onixary.sscPrimalstinct.client.selection.FormSelectionScreen) {
                    client.setScreen(new net.onixary.sscPrimalstinct.client.selection.FormSelectionScreen(list));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(SelectionPackets.CONFIRMED_S2C, (client, handler, buf, rs) -> {
            client.execute(() -> {
                net.onixary.sscPrimalstinct.client.selection.ClientSelectionState.onConfirmed();
                if (client.currentScreen instanceof net.onixary.sscPrimalstinct.client.selection.FormSelectionScreen) {
                    client.setScreen(null);
                }
            });
        });

        // tick：蜷缩键 + 无书快捷访问键 + pending 时打开选择界面
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CurlSleepKeybinding.tick();
            BookAccessKeybindings.tick(client);
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
            net.onixary.sscPrimalstinct.client.effect.PrimalstinctLockCinematic.clear();
            net.onixary.sscPrimalstinct.client.network.WanderInputClientState.clear();
        });

        SSCPrimalstinct.LOGGER.info("SSC Primalstinct client initialized");
    }
}
