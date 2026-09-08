package net.onixary.sscPrimalstinct.mixin.vanilla.client;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.onixary.sscPrimalstinct.client.network.PerceptionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ItemStack.class)
public abstract class InstinctItemTextMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void primalstinct$name(CallbackInfoReturnable<Text> cir) {
        String text = PerceptionClientState.replacement((ItemStack)(Object)this);
        if (text != null) cir.setReturnValue(Text.literal(text));
    }
    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void primalstinct$tooltip(CallbackInfoReturnable<java.util.List<Text>> cir) {
        String text = PerceptionClientState.replacement((ItemStack)(Object)this);
        if (text != null) cir.setReturnValue(new java.util.ArrayList<>(java.util.List.of(Text.literal(text))));
    }
}
