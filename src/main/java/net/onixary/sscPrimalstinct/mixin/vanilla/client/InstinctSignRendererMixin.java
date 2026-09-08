package net.onixary.sscPrimalstinct.mixin.vanilla.client;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.text.OrderedText;
import net.onixary.sscPrimalstinct.client.network.PerceptionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(SignBlockEntityRenderer.class)
public abstract class InstinctSignRendererMixin {
    @ModifyArg(method = "renderText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"), index = 0)
    private OrderedText primalstinct$sign(OrderedText text) { return PerceptionClientState.scrambleSign(text); }
    @ModifyArg(method = "renderText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"), index = 0)
    private OrderedText primalstinct$glowingSign(OrderedText text) { return PerceptionClientState.scrambleSign(text); }
}
