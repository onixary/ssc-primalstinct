package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.onixary.sscPrimalstinct.interaction.InteractionRuleManager;
import net.onixary.sscPrimalstinct.interaction.ToolDropHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡10（V2）：武器命中成功后掉落（attack 仅在有目标时被调用，空挥不进入本方法；
 * 完成时掉真实剩余堆栈——耐久已在 attack 内结算）。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "attack", at = @At("TAIL"))
    private void primalstinct$dropWeaponAfterHit(Entity target, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        InteractionRuleManager.InteractionRule rule = InteractionRuleManager.ruleIfRestricted(self);
        if (rule == null || !rule.dropToolAfterUse()) {
            return;
        }
        // 行为开始（HEAD 概念上）到 TAIL 同 tick，主手堆栈身份不会中途变化；直接掉真实堆栈
        ItemStack stack = self.getStackInHand(Hand.MAIN_HAND);
        ToolDropHelper.dropIfSameInstance(self, Hand.MAIN_HAND, stack);
    }
}
