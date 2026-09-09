package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * ssc-primalstinct:nearby_entity —— 半径内存在指定类型存活实体则成立（Apoli 只有
 * block_in_radius，没有实体版）。require_visibility 时要求玩家视线可见（射线判定）。
 * 注意：条件每次求值都会做一次小范围实体查询，供 modify_primalstinct_rate 周期扫描使用。
 */
public final class NearbyEntityCondition {

    private NearbyEntityCondition() {
    }

    public static ConditionFactory<Entity> getFactory() {
        return new ConditionFactory<>(Identifier.of(SSCPrimalstinct.MOD_ID, "nearby_entity"),
                new SerializableData()
                        .add("entity", SerializableDataTypes.IDENTIFIER)
                        .add("radius", SerializableDataTypes.DOUBLE, 8.0)
                        .add("require_visibility", SerializableDataTypes.BOOLEAN, false),
                (data, entity) -> entity instanceof ServerPlayerEntity player && matches(player,
                        data.getId("entity"), data.getDouble("radius"), data.getBoolean("require_visibility")));
    }

    private static boolean matches(ServerPlayerEntity player, Identifier entityId, double radius, boolean requireVisibility) {
        if (!Double.isFinite(radius) || radius <= 0) return false;
        double range = Math.min(radius, 128);
        EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(entityId).orElse(null);
        if (type == null) return false;
        boolean found = false;
        for (var target : player.getServerWorld().getEntitiesByType(type,
                player.getBoundingBox().expand(range), e -> e.isAlive() && !e.isSpectator())) {
            if (player.squaredDistanceTo(target) > range * range) continue;
            if (requireVisibility && !player.canSee(target)) continue;
            found = true;
            break;
        }
        return found;
    }
}
