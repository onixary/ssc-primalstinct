package net.onixary.sscPrimalstinct.mixin.ssc;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Preserve SSC's tick, threshold and cleanup sequence for unmanaged players. */
@Mixin(value = InstinctUtils.class, remap = false)
public abstract class InstinctUtilsMixin {
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;getPlayerList()Ljava/util/List;", remap = true))
    private static List<ServerPlayerEntity> primalstinct$legacyPlayers(PlayerManager manager) {
        return manager.getPlayerList().stream().filter(player -> !PrimalstinctLifecycle.suppressLegacy(player)).toList();
    }
    @Inject(target = @Desc(value = "addInstinctEffect", args = {PlayerEntity.class, InstinctUtils.InstinctEffect.class, boolean.class}),
            at = @At("HEAD"), cancellable = true)
    private static void primalstinct$guardEffect(PlayerEntity player, InstinctUtils.InstinctEffect effect,
                                                boolean immediate, CallbackInfo ci) {
        if (PrimalstinctLifecycle.suppressLegacy(player)) ci.cancel();
    }
    // clearInstinct only changes SSC's component: always allow transition cleanup.
}
