package net.onixary.sscPrimalstinct.client.ui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;

/**
 * 卡03 开发期 HUD：左上角显示同步快照（value/level/locked/revision + 客户端时延），
 * 用于肉眼验证 S2C 协议与持久化。正式 Instinct HUD 与预警表现在卡15 落地时替换本类。
 */
public final class DebugHudPlaceholder {

    private DebugHudPlaceholder() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(DebugHudPlaceholder::render);
        SSCPrimalstinct.LOGGER.debug("Primalstinct dev HUD registered");
    }

    private static void render(DrawContext drawContext, float tickDelta) {
        if (!SSCPrimalstinct.isDevelopmentEnvironment()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        PrimalstinctStateS2C snapshot = ClientPrimalstinctState.snapshot();
        if (snapshot == null) {
            drawContext.drawTextWithShadow(client.textRenderer, "[primalstinct] no snapshot", 4, 4, 0xFFFF5555);
            return;
        }
        // 外推仅用于显示（条形长度语义）：value + rate × 经过 tick / 20，钳制到 [0,100]
        long ticksSince = Math.max(0, (client.player == null ? 0 : client.player.age) - ClientPrimalstinctState.clientTickReceived());
        float extrapolated = Math.max(0.0f, Math.min(100.0f,
                snapshot.value() + snapshot.rate() * ticksSince / 20.0f));
        String line = String.format("[primalstinct] %.2f~%.2f L%d%s rev%d rate%.4f/s",
                snapshot.value(), extrapolated, snapshot.level(), snapshot.locked() ? " [LOCKED]" : "",
                snapshot.revision(), snapshot.rate());
        drawContext.drawTextWithShadow(client.textRenderer, line, 4, 4, 0xFFFFFFAA);
    }
}
