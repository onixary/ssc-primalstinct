package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.entity.player.PlayerInventory;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡09 UI 反馈：滚轮只在允许槽位间移动并正确回绕（消除"本地选择→服务端拒绝→回弹"的高亮跳变）。
 * 允许集为连续的 [0, H)；当前若处于非法槽（异常态）则吸附到 0。
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryClientMixin {

    @Shadow
    public int selectedSlot;

    @Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
    private void primalstinct$scrollWithinAllowed(double scroll, CallbackInfo ci) {
        if (!ClientPrimalstinctState.inventoryRestricted()) {
            return;
        }
        ci.cancel();
        int allowed = Math.max(1, Math.min(9, ClientPrimalstinctState.allowedHotbar()));
        int current = this.selectedSlot;
        if (current < 0 || current >= allowed) {
            this.selectedSlot = 0;  // 异常态吸附
            return;
        }
        int next = scroll < 0.0 ? current + 1 : current - 1;
        if (next < 0) {
            next = allowed - 1;
        }
        if (next >= allowed) {
            next = 0;
        }
        this.selectedSlot = next;
    }
}
