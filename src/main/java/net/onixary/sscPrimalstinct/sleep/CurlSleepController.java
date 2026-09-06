package net.onixary.sscPrimalstinct.sleep;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityPose;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctService;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctSource;
import net.onixary.sscPrimalstinct.power.factory.CurlSleepPower;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ground rest is distinct from actual sleep; vanilla handles the 100-tick timer and night skip. */
public final class CurlSleepController {
    public static final String SLEEP_RATE_KEY = "sleep:rate";
    private record Session(ServerWorld world, Vec3d position) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private CurlSleepController() {}

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> wakeUp(handler.player, "disconnect"));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    public static boolean isCurlSleeping(ServerPlayerEntity player) {
        return SESSIONS.containsKey(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        if (isCurlSleeping(player)) {
            wakeUp(player, "key");
            return;
        }
        CurlSleepPower power = findActivePower(player);
        if (power == null || !player.isAlive() || player.isSpectator() || player.isSleeping()) return;
        if (!player.isOnGround()) {
            player.sendMessage(Text.translatable("ssc-primalstinct.sleep.need_ground"), true);
            return;
        }
        if (player.hasVehicle()) {
            player.sendMessage(Text.translatable("ssc-primalstinct.sleep.no_vehicle"), true);
            return;
        }
        SESSIONS.put(player.getUuid(), new Session(player.getServerWorld(), player.getPos()));
        ((CurlSleepState) player).primalstinct$setCurled(true);
        player.setVelocity(Vec3d.ZERO);
        player.setPose(EntityPose.SLEEPING);
        updateSleep(player, power);
    }

    private static void updateSleep(ServerPlayerEntity player, CurlSleepPower power) {
        boolean canSleep = power.isAllowSleep() && player.getWorld().getDimension().bedWorks()
                && !player.getWorld().isDay();
        if (canSleep && !player.isSleeping()) {
            ((CurlSleepState) player).primalstinct$resetSleepTimer();
            player.setSleepingPosition(player.getBlockPos());
            // Merely setting SLEEPING_POSITION never updates SleepManager's cached count.
            player.getServerWorld().updateSleepingPlayers();
        } else if (!canSleep && player.isSleeping()) {
            wakeUp(player, "sleep_no_longer_allowed");
            return;
        }
        PrimalstinctService.setRate(player, SLEEP_RATE_KEY, PrimalstinctSource.POWER,
                power.getInstinctRateWhileSleeping());
    }

    public static void wakeUp(ServerPlayerEntity player, String reason) {
        if (!isCurlSleeping(player)) return;
        // Also covers the sleep-screen Leave Bed button and the world's automatic morning wake.
        player.wakeUp(true, true);
        SSCPrimalstinct.LOGGER.debug("[primalstinct] curl ended: {} ({})", player.getGameProfile().getName(), reason);
    }

    /** Called before PlayerEntity delegates to LivingEntity's bed-specific wake/position logic. */
    public static void onVanillaWake(ServerPlayerEntity player) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session == null) return;
        player.clearSleepingPosition();
        ((CurlSleepState) player).primalstinct$setCurled(false);
        player.setPose(EntityPose.STANDING);
        PrimalstinctService.removeRate(player, SLEEP_RATE_KEY);
        session.world().updateSleepingPlayers();
    }

    public static void validate(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) return;
        CurlSleepPower power = findActivePower(player);
        if (power == null || !player.isAlive() || player.isSpectator() || player.hasVehicle()
                || session.world() != player.getServerWorld()
                || player.getPos().squaredDistanceTo(session.position()) > 1.0) {
            wakeUp(player, "invalid_state");
            return;
        }
        // Sleeping movement packets/pose updates do not reliably preserve onGround.
        // Test physical support instead of treating a transient flag as a wake request.
        if (player.getWorld().isSpaceEmpty(player, player.getBoundingBox().offset(0, -0.1, 0))) {
            wakeUp(player, "lost_support");
            return;
        }
        updateSleep(player, power);
    }

    public static @Nullable CurlSleepPower findActivePower(ServerPlayerEntity player) {
        for (CurlSleepPower power : PowerHolderComponent.getPowers(player, CurlSleepPower.class)) {
            if (power.isActive()) return power;
        }
        return null;
    }
}
