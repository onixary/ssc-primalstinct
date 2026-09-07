package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.instinct.WanderAiController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 游荡 AI 接管（MIXIN_INVENTORY.md V4）：捕获服务端收到的玩家按键输入。
 * MC 1.20.1 仅在骑乘时发送 PlayerInputC2SPacket，这里只提供补充信号。
 * 普通步行输入由 WanderInputClientState 在空闲及接管期间经 C2S 上报。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityInputMixin {

    @Inject(method = "updateInput", at = @At("HEAD"))
    private void primalstinct$trackWanderInput(float sideways, float forward, boolean jumping, boolean sneaking,
                                               CallbackInfo ci) {
        if (sideways != 0.0f || forward != 0.0f || jumping || sneaking) {
            WanderAiController.markDirectInput((ServerPlayerEntity) (Object) this);
        }
    }
}
