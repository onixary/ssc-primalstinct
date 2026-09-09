package net.onixary.sscPrimalstinct.network;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.power.factory.InstinctOverheatPower;
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
                boolean hasHeat = false; float heatMeter = 0; int heatTargets = 0; boolean heatFrozen = false;
                java.util.Set<Integer> watch = new java.util.HashSet<>();
                if (player.isAlive() && PrimalstinctLifecycle.isManaged(player)) {
                    for (var power : PowerHolderComponent.getPowers(player, InstinctPerceptionPower.class)) {
                        if (!power.isActive()) continue;
                        codex = Math.max(codex, power.codex); palette = Math.max(palette, power.palette);
                        signs = Math.max(signs, power.signs);
                        if (!power.itemText.isEmpty()) { itemText = power.itemText; tags.add(power.exemptTag); }
                        if (power.radius > 0) targets.addAll(WanderAiController.nearbyAttackTargets(player, power.radius, power.sensor, true));
                        // 注视组：指定实体在半径内（可要求可见）→ 客户端绿色描边 + watchPresent 供速率扫描
                        if (power.watchEntity != null && power.watchRadius > 0) {
                            var type = net.minecraft.registry.Registries.ENTITY_TYPE.getOrEmpty(power.watchEntity).orElse(null);
                            if (type != null) {
                                for (var target : player.getServerWorld().getEntitiesByType(type,
                                        player.getBoundingBox().expand(power.watchRadius),
                                        e -> e.isAlive() && !e.isSpectator() && e != player)) {
                                    if (player.squaredDistanceTo(target) > power.watchRadius * power.watchRadius) continue;
                                    if (power.watchRequireVisibility && !player.canSee(target)) continue;
                                    watch.add(target.getId());
                                }
                            }
                            power.watchPresent = !watch.isEmpty();
                        }
                    }
                    // 过热计量条开发读数（dev HUD 第二行）
                    for (var heat : PowerHolderComponent.getPowers(player, InstinctOverheatPower.class)) {
                        if (!heat.isActive()) continue;
                        hasHeat = true; heatMeter = heat.getMeter();
                        heatTargets = heat.getLastTargets(); heatFrozen = heat.isFrozen();
                        break;
                    }
                }
                var buf = PacketByteBufs.create();
                buf.writeIdentifier(player.getWorld().getRegistryKey().getValue());
                buf.writeFloat(codex); buf.writeFloat(palette); buf.writeFloat(signs); buf.writeString(itemText);
                buf.writeVarInt(tags.size()); for (var tag : tags) buf.writeIdentifier(tag);
                buf.writeVarInt(targets.size()); for (int id : targets) buf.writeVarInt(id);
                buf.writeBoolean(hasHeat); buf.writeFloat(heatMeter);
                buf.writeVarInt(heatTargets); buf.writeBoolean(heatFrozen);
                buf.writeVarInt(watch.size()); for (int id : watch) buf.writeVarInt(id);
                ServerPlayNetworking.send(player, ID, buf);
            }
        });
    }
}
