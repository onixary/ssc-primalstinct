package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡09 UI 反馈：背包/容器界面中锁定的玩家库存槽绘制深灰占位遮罩
 * （复用原版槽位底图，叠加半透明深灰作为明确禁用标识）。
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void primalstinct$dimLockedSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!(slot.inventory instanceof PlayerInventory)) {
            return;
        }
        if (ClientPrimalstinctState.isLockedSlot(slot.getIndex())) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA01E1E1E);
        }
    }
}
