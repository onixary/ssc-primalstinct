package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.onixary.sscPrimalstinct.client.network.WanderMotionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputOverheatingMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void primalstinct$blockManualMovement(boolean slowDown, float factor, CallbackInfo ci) {
        if (!WanderMotionClientState.isForced()) return;
        movementForward = 0;
        movementSideways = 0;
        pressingForward = pressingBack = pressingLeft = pressingRight = jumping = sneaking = false;
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.setSprinting(false);
    }
}
