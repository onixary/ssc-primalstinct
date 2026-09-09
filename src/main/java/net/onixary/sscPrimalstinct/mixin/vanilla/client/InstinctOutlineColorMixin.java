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
        if (!entity.getWorld().isClient) return;
        // 注视组命中：绿色（豹猫等）；猎物描边：红色。两组互斥时注视优先。
        if (PerceptionClientState.watched(entity.getId())) cir.setReturnValue(0x30ff30);
        else if (PerceptionClientState.outlined(entity.getId())) cir.setReturnValue(0xff3030);
    }
}
