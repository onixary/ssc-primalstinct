package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.interaction.InteractionRestrictions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡09/卡10 UI 反馈：
 * 背包/容器界面中锁定的玩家库存槽绘制深灰占位遮罩（复用原版槽位底图，叠加半透明深灰作为明确禁用标识）；
 * prevent_inventory_crafting 生效时，背包 2×2（及 include_crafting_table 时的工作台 3×3）
 * 合成输入/结果槽绘制暗红遮罩提示禁用（取结果本身已被 Slot/ScreenHandler 层拦截）。
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    @Final
    protected ScreenHandler handler;

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void primalstinct$dimLockedSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        if (slot.inventory instanceof PlayerInventory) {
            if (ClientPrimalstinctState.isLockedSlot(slot.getIndex())) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA01E1E1E);
            }
            return;
        }
        if (!(slot.inventory instanceof RecipeInputInventory)
                && !(slot.inventory instanceof CraftingResultInventory)) {
            return;
        }
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && InteractionRestrictions.blocksCrafting(player, this.handler)) {
            context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA04A0A0A);
        }
    }
}
