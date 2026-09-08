package net.onixary.sscPrimalstinct.mixin.vanilla.client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.onixary.sscPrimalstinct.client.network.PerceptionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MinecraftClient.class)
public abstract class InstinctOutlineMixin {
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void primalstinct$outline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PerceptionClientState.outlined(entity.getId())) cir.setReturnValue(true);
    }
}
