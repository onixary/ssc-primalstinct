package net.onixary.sscPrimalstinct.client.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.onixary.sscPrimalstinct.network.WanderMotionS2C;
import net.onixary.sscPrimalstinct.instinct.WanderJumpImpulse;

/** Apply AI intent before vanilla movement; vanilla owns gravity and position packets. */
@Environment(EnvType.CLIENT)
public final class WanderMotionClientState {
    private static boolean active;
    private static boolean waitingForRelease;
    private static double x;
    private static double z;
    private static final WanderJumpImpulse JUMP = new WanderJumpImpulse();
    private static int remainingTicks;

    private WanderMotionClientState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WanderMotionS2C.ID, (client, handler, buf, sender) -> {
            WanderMotionS2C motion = WanderMotionS2C.read(buf);
            client.execute(() -> accept(motion));
        });
        ClientTickEvents.START_CLIENT_TICK.register(WanderMotionClientState::tick);
    }

    private static void accept(WanderMotionS2C motion) {
        if (!motion.active()) {
            clear();
            return;
        }
        if (waitingForRelease) return;
        active = true;
        x = motion.x();
        z = motion.z();
        // A later horizontal-only packet must not erase an unconsumed jump.
        JUMP.accept(motion.jump());
        remainingTicks = 20;
    }

    public static void manualInput() {
        waitingForRelease |= active;
        active = false;
        JUMP.clear();
    }

    public static void clear() {
        active = false;
        waitingForRelease = false;
        JUMP.clear();
        remainingTicks = 0;
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null) {
            clear();
            return;
        }
        if (!active) return;
        if (WanderInputClientState.hasMovementInput(client)) {
            manualInput();
            return;
        }
        if (--remainingTicks <= 0 || !client.player.isAlive() || client.player.hasVehicle()
                || client.player.isSpectator() || client.player.getAbilities().flying) {
            clear();
            return;
        }
        Vec3d velocity = client.player.getVelocity();
        double y = JUMP.consume(velocity.y, client.player.isOnGround());
        client.player.setVelocity(x, y, z);
    }
}
