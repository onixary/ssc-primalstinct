package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.sleep.CurlSleepController;
import net.onixary.sscPrimalstinct.sleep.CurlSleepState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerCurlSleepMixin implements CurlSleepState {
    @Unique private static final TrackedData<Boolean> PRIMALSTINCT_CURLED =
            DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Shadow private int sleepTimer;

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void primalstinct$initCurl(CallbackInfo ci) {
        ((PlayerEntity) (Object) this).getDataTracker().startTracking(PRIMALSTINCT_CURLED, false);
    }

    @Override public boolean primalstinct$isCurled() {
        return ((PlayerEntity) (Object) this).getDataTracker().get(PRIMALSTINCT_CURLED);
    }

    @Override public void primalstinct$setCurled(boolean value) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        player.getDataTracker().set(PRIMALSTINCT_CURLED, value);
        if (player instanceof ServerPlayerEntity sp) {
            // Send the marker before the sleeping-position update triggers client bed positioning.
            var packet = new net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket(
                    player.getId(), java.util.List.of(DataTracker.SerializedEntry.of(PRIMALSTINCT_CURLED, value)));
            sp.networkHandler.sendPacket(packet);
            sp.getServerWorld().getChunkManager().sendToOtherNearbyPlayers(sp, packet);
        }
    }

    @Override public void primalstinct$resetSleepTimer() { sleepTimer = 0; }

    @Inject(method = "updatePose", at = @At("TAIL"))
    private void primalstinct$restPose(CallbackInfo ci) {
        if (primalstinct$isCurled()) ((PlayerEntity) (Object) this).setPose(EntityPose.SLEEPING);
    }

    @Inject(method = "wakeUp(ZZ)V", at = @At("HEAD"))
    private void primalstinct$finishCurl(boolean skipTimer, boolean updatePlayers, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity sp) CurlSleepController.onVanillaWake(sp);
    }
}
