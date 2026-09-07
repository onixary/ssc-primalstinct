package net.onixary.sscPrimalstinct.mixin.ssc.client;

import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡15/卡17（MIXIN_INVENTORY.md S6）：停用旧 SSC 本能条，避免两根本能条并存。
 * 取消条件为客户端接管态（快照 managed || selectionPending，见 ClientPrimalstinctState.suppressLegacy）：
 * 接管时旧条取消、由 PrimalInstinctHud 显示新条（含 NoInstinct 形态——显隐不读该 flag）；
 * 未接管（普通 SSC 路线 / 连到无附属服务器）时旧条照常渲染。
 * 不参与 SSC 的 mana OverrideInstinctBar 顶替链：法力条由 SSC 照常渲染，新条自行避让。
 */
@Mixin(value = InstinctBarRenderer.class, remap = false)
public abstract class InstinctBarRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void primalstinct$hideOldInstinctBar(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ClientPrimalstinctState.suppressLegacy()) {
            ci.cancel();
        }
    }
}
