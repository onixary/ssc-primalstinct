package net.onixary.sscPrimalstinct.instinct;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;
import net.onixary.sscPrimalstinct.power.PrimalPowerReconciler;
import net.onixary.sscPrimalstinct.selection.SelectionSessionManager;
import net.onixary.sscPrimalstinct.sleep.CurlSleepController;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Both entry routes share ownership rules. Dedicated servers never load client classes. */
public final class PrimalstinctLifecycle {
    private static final Map<UUID, Boolean> MANAGED = new HashMap<>();
    private static Predicate<PlayerEntity> clientManaged = player -> false;
    private PrimalstinctLifecycle() {}
    public static void setClientManaged(Predicate<PlayerEntity> predicate) { clientManaged = predicate; }

    public static boolean isManaged(PlayerEntity player) {
        if (player.getWorld().isClient()) return clientManaged.test(player);
        if (!(player instanceof ServerPlayerEntity sp) || SelectionSessionManager.isPending(sp)) return false;
        Identifier form = SSCAdapter.currentFormIdentifier(player);
        return form != null && PrimalRosterManager.resolve(form) != null;
    }
    public static boolean suppressLegacy(PlayerEntity player) {
        return isManaged(player) || player instanceof ServerPlayerEntity sp && SelectionSessionManager.isPending(sp);
    }
    public static void refresh(ServerPlayerEntity player) {
        boolean managed = isManaged(player);
        Boolean previous = MANAGED.put(player.getUuid(), managed);
        if (managed) RegPrimalstinctComponent.PRIMALSTINCT.get(player).initializeInstinct();
        if (previous == null || previous != managed) {
            PrimalstinctService.clearPlayer(player.getUuid());
            CurlSleepController.wakeUp(player, "ownership_change");
            SSCAdapter.invalidateInstinctRate(player);
            PrimalPowerReconciler.requestReconcile(player);
            PrimalstinctNetwork.syncNow(player);
        }
    }
    public static void forget(UUID uuid) { MANAGED.remove(uuid); }
    public static void clear() { MANAGED.clear(); }
}
