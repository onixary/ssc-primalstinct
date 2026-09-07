package net.onixary.sscPrimalstinct.mixin.ssc;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsC2S;
import net.onixary.sscPrimalstinct.selection.SelectionSessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The book still starts SSC normally; only a live form selection takes precedence. */
@Mixin(value = ModPacketsC2S.class, remap = false)
public abstract class StartBookPacketMixin {
    @Inject(method = "onPressStartBookButton", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$guardStart(MinecraftServer server, ServerPlayerEntity player,
            ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender, CallbackInfo ci) {
        if (SelectionSessionManager.isPending(player)) ci.cancel();
    }
}
