package net.onixary.sscPrimalstinct.mixin.vanilla.client;
import net.minecraft.entity.Entity;
import net.onixary.sscPrimalstinct.client.network.PerceptionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class InstinctOutlineColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void primalstinct$color(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity)(Object)this;
        if (entity.getWorld().isClient && PerceptionClientState.outlined(entity.getId())) cir.setReturnValue(0xff3030);
    }
}
