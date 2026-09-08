package net.onixary.sscPrimalstinct.network;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.power.factory.InstinctPerceptionPower;
import net.onixary.sscPrimalstinct.instinct.*;
/** Ten-tick refresh also removes effects after death, Power removal, or a form change. */
public final class PerceptionSync {
    public static final Identifier ID = new Identifier("ssc-primalstinct", "perception");
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            for (var player : server.getPlayerManager().getPlayerList()) {
                float codex = 0, palette = 0, signs = 0;
                String itemText = "";
                java.util.Set<Identifier> tags = new java.util.HashSet<>();
                java.util.Set<Integer> targets = new java.util.HashSet<>();
                if (player.isAlive() && PrimalstinctLifecycle.isManaged(player)) {
                    for (var power : PowerHolderComponent.getPowers(player, InstinctPerceptionPower.class)) {
                        if (!power.isActive()) continue;
                        codex = Math.max(codex, power.codex); palette = Math.max(palette, power.palette);
                        signs = Math.max(signs, power.signs);
                        if (!power.itemText.isEmpty()) { itemText = power.itemText; tags.add(power.exemptTag); }
                        if (power.radius > 0) targets.addAll(WanderAiController.nearbyAttackTargets(player, power.radius, power.sensor, true));
                    }
                }
                var buf = PacketByteBufs.create();
                buf.writeIdentifier(player.getWorld().getRegistryKey().getValue());
                buf.writeFloat(codex); buf.writeFloat(palette); buf.writeFloat(signs); buf.writeString(itemText);
                buf.writeVarInt(tags.size()); for (var tag : tags) buf.writeIdentifier(tag);
                buf.writeVarInt(targets.size()); for (int id : targets) buf.writeVarInt(id);
                ServerPlayNetworking.send(player, ID, buf);
            }
        });
    }
}
