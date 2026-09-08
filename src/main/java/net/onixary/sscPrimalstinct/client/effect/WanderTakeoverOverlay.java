package net.onixary.sscPrimalstinct.client.effect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.client.network.WanderMotionClientState;

/** Vanilla nausea texture/additive blend, tinted yellow; no camera distortion. */
@Environment(EnvType.CLIENT)
public final class WanderTakeoverOverlay {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/misc/nausea.png");

    private WanderTakeoverOverlay() {
    }

    public static void render(DrawContext context) {
        if (!WanderMotionClientState.isActive()) return;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
        try {
            context.setShaderColor(0.225f, 0.21f, 0.1f, 1.0f);
            context.drawTexture(TEXTURE, 0, 0, -90, 0.0f, 0.0f, width, height, width, height);
        } finally {
            context.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }
}
