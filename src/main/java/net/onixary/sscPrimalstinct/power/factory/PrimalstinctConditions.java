package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.util.Comparison;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;

/**
 * 卡07：primalstinct_value / primalstinct_level / primalstinct_locked 三条件。
 * value 在锁定时恒 false（满值仍保持限制条件的策划要求由 level/locked 承载）；
 * level/locked 读取真实组件状态。客户端条件下同样读快照外的服务端同步值——
 * 条件评估仅用于服务端 Power 激活判定（卡06 结算在主线程）。
 */
public final class PrimalstinctConditions {

    private PrimalstinctConditions() {
    }

    public static ConditionFactory<Entity> valueCondition() {
        return new ConditionFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "primalstinct_value"),
                new SerializableData()
                        .add("comparison", ApoliDataTypes.COMPARISON)
                        .add("compare_to", SerializableDataTypes.FLOAT, 0.0f),
                PrimalstinctConditions::valueTest
        );
    }

    private static boolean valueTest(SerializableData.Instance data, Entity entity) {
        PlayerState state = PlayerState.of(entity);
        if (state == null) {
            return false;
        }
        if (state.locked()) {
            return false;  // 锁定时恒 false
        }
        Comparison comparison = (Comparison) data.get("comparison");
        return comparison != null && comparison.compare(state.value(), data.getFloat("compare_to"));
    }

    public static ConditionFactory<Entity> levelCondition() {
        return new ConditionFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "primalstinct_level"),
                new SerializableData()
                        .add("comparison", ApoliDataTypes.COMPARISON)
                        .add("compare_to", SerializableDataTypes.INT, 0),
                PrimalstinctConditions::levelTest
        );
    }

    private static boolean levelTest(SerializableData.Instance data, Entity entity) {
        PlayerState state = PlayerState.of(entity);
        if (state == null) {
            return false;
        }
        Comparison comparison = (Comparison) data.get("comparison");
        return comparison != null && comparison.compare(state.level(), data.getInt("compare_to"));
    }

    public static ConditionFactory<Entity> lockedCondition() {
        return new ConditionFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "primalstinct_locked"),
                new SerializableData(),
                (data, entity) -> {
                    PlayerState state = PlayerState.of(entity);
                    return state != null && state.locked();
                }
        );
    }

    private record PlayerState(float value, int level, boolean locked) {
        static PlayerState of(Entity entity) {
            if (!(entity instanceof ServerPlayerEntity player)) {
                // 客户端条件评估：本附属规则判定一律服务端，客户端返回保守 false
                if (entity instanceof PlayerEntity clientPlayer && clientPlayer.getWorld().isClient()) {
                    return null;
                }
                return null;
            }
            var component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
            return new PlayerState(component.getValue(), component.getLevel(), component.isLocked());
        }
    }
}
