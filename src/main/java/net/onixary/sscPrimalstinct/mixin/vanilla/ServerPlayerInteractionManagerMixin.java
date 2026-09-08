package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.interaction.InteractionRuleManager;
import net.onixary.sscPrimalstinct.interaction.ToolDropHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡10（V2）：方块真正破坏成功后掉落工具（tryBreakBlock 返回 true 才触发；
 * 挖掘开始/取消走 processBlockBreakingAction，不经过这里——天然区分成功与取消）。
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {

    @Shadow
    public ServerPlayerEntity player;

    // The packet handler still runs forceMainThread, updateSequence and vanilla block correction.
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void primalstinct$denyInteraction(ServerPlayerEntity player, net.minecraft.world.World world,
            net.minecraft.item.ItemStack stack, Hand hand, net.minecraft.util.hit.BlockHitResult hit,
            CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        if (net.onixary.sscPrimalstinct.interaction.InteractionRestrictions.failsInteraction(player, hand, hit)) {
            // Delta sync sees no server inventory change after a rejected client prediction.
            player.currentScreenHandler.syncState();
            player.playerScreenHandler.syncState();
            cir.setReturnValue(net.minecraft.util.ActionResult.FAIL);
        }
    }

    @org.spongepowered.asm.mixin.Unique private net.minecraft.item.ItemStack primalstinct$miningStack;
    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void primalstinct$rememberTool(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        primalstinct$miningStack = player.getMainHandStack();
    }
    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void primalstinct$dropToolAfterBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        InteractionRuleManager.InteractionRule rule = InteractionRuleManager.ruleIfRestricted(this.player);
        if (rule == null || !rule.dropToolAfterUse()) {
            return;
        }
        ToolDropHelper.dropIfSameInstance(this.player, Hand.MAIN_HAND, primalstinct$miningStack);
    }
}
