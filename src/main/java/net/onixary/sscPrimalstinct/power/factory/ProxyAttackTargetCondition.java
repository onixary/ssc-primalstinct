package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.instinct.WanderAiController;

public final class ProxyAttackTargetCondition {
    private ProxyAttackTargetCondition() {
    }
    public static ConditionFactory<Entity> getFactory() {
        return new ConditionFactory<>(Identifier.of(SSCPrimalstinct.MOD_ID, "proxy_has_nearby_attack_target"),
                new SerializableData().add("radius", SerializableDataTypes.DOUBLE, 16.0),
                (data, entity) -> entity instanceof ServerPlayerEntity player
                        && WanderAiController.hasNearbyAttackTarget(player, data.getDouble("radius")));
    }
}
