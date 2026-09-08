package net.onixary.sscPrimalstinct.client.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.onixary.sscPrimalstinct.client.network.WanderMotionClientState;

/**
 * Vanilla nausea texture/additive blend, no camera distortion.
 * 接管生效期间在暗黄与琥珀橙两色间按正弦呼吸波动（相位自每次接管生效起算）；
 * 生效淡入、失效淡出（smoothstep），淡出途中接管恢复则从当前强度继续淡入。
 * 颜色与频率为常量，直接改这里调表现。
 */
@Environment(EnvType.CLIENT)
public final class WanderTakeoverOverlay {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/misc/nausea.png");

    /** 波动两端：暗黄 ↔ 琥珀橙（additive 混合下的 shader 颜色）。 */
    private static final float[] COLOR_DIM = {0.16f, 0.15f, 0.05f};
    private static final float[] COLOR_WARM = {0.34f, 0.19f, 0.045f};
    /** 呼吸频率，每秒完整波动次数。 */
    private static final float PULSE_HZ = 0.6f;
    private static final float FADE_IN_SECONDS = 0.5f;
    private static final float FADE_OUT_SECONDS = 0.5f;

    /** 淡入淡出的线性进度 0..1，按帧间隔推进；<=0 时不渲染。 */
    private static float fade;
    private static long phaseStartMillis;
    private static long lastFrameMillis = Long.MIN_VALUE;

    private WanderTakeoverOverlay() {
    }

    public static void render(DrawContext context) {
        long now = Util.getMeasuringTimeMs();
        float deltaSeconds = lastFrameMillis == Long.MIN_VALUE ? 0.0f
                : Math.max(0.0f, Math.min(0.25f, (now - lastFrameMillis) / 1000.0f));
        lastFrameMillis = now;

        boolean takeover = WanderMotionClientState.isActive();
        if (takeover && fade <= 0.0f) {
            phaseStartMillis = now;  // 新一轮接管：波动从 COLOR_DIM 相位起
        }
        fade = Math.max(0.0f, Math.min(1.0f, fade
                + (takeover ? deltaSeconds / FADE_IN_SECONDS : -deltaSeconds / FADE_OUT_SECONDS)));
        if (fade <= 0.0f) return;

        float intensity = fade * fade * (3.0f - 2.0f * fade);
        float pulse = (float) (Math.sin((now - phaseStartMillis) / 1000.0 * Math.PI * 2.0 * PULSE_HZ) + 1.0) * 0.5f;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
        try {
            context.setShaderColor(
                    lerp(COLOR_DIM[0], COLOR_WARM[0], pulse) * intensity,
                    lerp(COLOR_DIM[1], COLOR_WARM[1], pulse) * intensity,
                    lerp(COLOR_DIM[2], COLOR_WARM[2], pulse) * intensity,
                    1.0f);
            context.drawTexture(TEXTURE, 0, 0, -90, 0.0f, 0.0f, width, height, width, height);
        } finally {
            context.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
