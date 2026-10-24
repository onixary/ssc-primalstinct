package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.onixary.shapeShifterCurseFabric.custom_ui.FormUpgradeScreen;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers the SSC perk key and server-requested menus before the screen is installed. */
@Mixin(MinecraftClient.class)
public abstract class PrimalPerkScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void primalstinct$blockPerkScreen(Screen screen, CallbackInfo ci) {
        var player = ((MinecraftClient) (Object) this).player;
        if (screen instanceof FormUpgradeScreen && player != null && SSCAdapter.blockPrimalAttunement(player)) {
            ci.cancel();
        }
    }
}
