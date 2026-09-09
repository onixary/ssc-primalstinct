package net.onixary.sscPrimalstinct.mixin.vanilla;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;
import net.onixary.sscPrimalstinct.power.factory.PreventBlockPlacePower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 白板"本能设计"：prevent_block_place 的 on_place_action 挂点——
 * 服务端真实放置成功（ACCEPTED）时触发；客户端拦截（L5 禁放）不会走到这里。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void primalstinct$onPlaceSuccess(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) return;
        if (!(context.getPlayer() instanceof ServerPlayerEntity player) || player.getWorld().isClient()) return;
        if (!PrimalstinctLifecycle.isManaged(player)) return;
        for (PreventBlockPlacePower power : PowerHolderComponent.getPowers(player, PreventBlockPlacePower.class)) {
            if (power.isActive() && power.getOnPlaceAction() != null) {
                power.getOnPlaceAction().accept(player);
            }
        }
    }
}
