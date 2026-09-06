package net.onixary.sscPrimalstinct.mixin.ssc.client;

import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡15（MIXIN_INVENTORY.md S6）：停用旧 SSC 本能条，避免两根本能条并存。
 * 快照存在 = 服务端运行本附属（卡03 登录即同步）→ 旧条取消；
 * 客户端连到未安装本附属的 SSC 服务器时无快照，旧条照常工作。
 * 旧条被取消的形态（含 NoInstinct）由新 PrimalInstinctHud 按 managed 规则接管显示。
 * 不参与 SSC 的 mana OverrideInstinctBar 顶替链：法力条由 SSC 照常渲染，新本能条自行避让。
 */
@Mixin(value = InstinctBarRenderer.class, remap = false)
public abstract class InstinctBarRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void primalstinct$hideOldInstinctBar(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ClientPrimalstinctState.snapshot() != null) {
            ci.cancel();
        }
    }
}
