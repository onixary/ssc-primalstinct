package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemUsageContext;
import net.onixary.sscPrimalstinct.interaction.InteractionRuleManager;
import net.onixary.sscPrimalstinct.interaction.ToolDropHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡10（V2）：铲地/剥皮/耕地等 useOnBlock 成功（ACCEPTED）后掉落工具；
 * context 自带真实堆栈与手位，失败（PASS/FAIL）不触发。仅服务端玩家。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void primalstinct$dropToolAfterUseOnBlock(ItemUsageContext context,
                                                       CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) {
            return;
        }
        PlayerEntity player = context.getPlayer();
        if (!(player instanceof ServerPlayerEntity serverPlayer) || player.getWorld().isClient()) {
            return;
        }
        InteractionRuleManager.InteractionRule rule = InteractionRuleManager.ruleIfRestricted(serverPlayer);
        if (rule == null || !rule.dropToolAfterUse()) {
            return;
        }
        for (Hand hand : Hand.values()) {
            if (serverPlayer.getStackInHand(hand) == context.getStack()) {
                ToolDropHelper.dropIfSameInstance(serverPlayer, hand, context.getStack());
                return;
            }
        }
    }
}
