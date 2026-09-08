package net.onixary.sscPrimalstinct.power.factory;
import io.github.apace100.apoli.power.*;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
public final class InteractionFailurePower extends Power {
    public final float blockChance, containerChance;
    public InteractionFailurePower(PowerType<?> type, LivingEntity entity, float blocks, float containers) {
        super(type, entity);
        blockChance = clamp(blocks); containerChance = clamp(containers);
    }
    private static float clamp(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    public static PowerFactory<?> getFactory() {
        return new PowerFactory<>(new Identifier("ssc-primalstinct", "interaction_failure"),
            new SerializableData().add("block_chance", SerializableDataTypes.FLOAT, 0f)
                .add("container_chance", SerializableDataTypes.FLOAT, 0f),
            data -> (type, entity) -> new InteractionFailurePower(type, entity,
                data.getFloat("block_chance"), data.getFloat("container_chance"))).allowCondition();
    }
}
