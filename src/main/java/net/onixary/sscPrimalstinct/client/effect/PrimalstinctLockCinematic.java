package net.onixary.sscPrimalstinct.client.effect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCClientAdapter;

/**
 * 满值锁定演出的客户端驱动：复用 SSC TransformOverlay（恶心/黑屏叠加层），
 * 强度曲线逐行复刻 SSC TransformManager.clientTick（入段恶心渐强→黑屏，出段渐退；
 * 出段除数沿用 SSC 原式的 DURATION_IN，保持观感一致）。
 * 不触碰 SSC 的 transformTimer/clientTransformState——变形动画与形态变更完全不发生；
 * 移动限制由服务端以缓慢 III 状态效果实现（不做完全输入锁）。
 */
@Environment(EnvType.CLIENT)
public final class PrimalstinctLockCinematic {

    private static int timer = -1;

    private PrimalstinctLockCinematic() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void start(int totalTicks) {
        timer = 0;
        SSCClientAdapter.transformOverlayEnable(true);
    }

    private static void tick() {
        if (timer < 0) {
            return;
        }
        int durationIn = SSCAdapter.transformFxDurationIn();
        int durationOut = SSCAdapter.transformFxDurationOut();
        float nauseaStrength;
        float blackStrength;
        if (timer < durationIn) {
            nauseaStrength = timer / (float) durationIn;
            blackStrength = Math.max(nauseaStrength - 0.8f, 0.0f) * 5.0f;
        } else if (timer < durationIn + durationOut) {
            nauseaStrength = 1.0f - ((timer - durationIn) / (float) durationIn);
            blackStrength = Math.min(1.0f, nauseaStrength / 0.6f);
        } else {
            stop();
            return;
        }
        SSCClientAdapter.transformOverlayNausea(nauseaStrength);
        SSCClientAdapter.transformOverlayBlack(blackStrength);
        timer++;
    }

    public static void stop() {
        timer = -1;
        SSCClientAdapter.transformOverlayEnable(false);
        SSCClientAdapter.transformOverlayNausea(0.0f);
        SSCClientAdapter.transformOverlayBlack(0.0f);
    }

    /** 断线/切换服务器时清理叠加层（未在演出中为幂等空操作）。 */
    public static void clear() {
        stop();
    }
}
