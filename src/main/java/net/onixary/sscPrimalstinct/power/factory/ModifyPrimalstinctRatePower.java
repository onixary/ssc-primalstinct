package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡07：ssc-primalstinct:modify_primalstinct_rate —— 持续速率贡献 Power。
 * 字段：source_id（稳定键）、rate_per_second（点/秒）；支持 condition（PowerFactory.allowCondition）。
 * 活跃时由 PrimalstinctService 每 tick 扫描并向内核贡献速率（稳定键 "power:<source_id>"），
 * 失活/撤销后扫描自动移除——不采用"获得 Power 即改值"的模型，重挂载不产生一次性变化。
 */
public class ModifyPrimalstinctRatePower extends Power {

    public static final String CONTRIBUTION_KEY_PREFIX = "power:";

    private final String sourceId;
    private final float ratePerSecond;

    public ModifyPrimalstinctRatePower(PowerType<?> type, LivingEntity entity,
                                       String sourceId, float ratePerSecond) {
        super(type, entity);
        this.sourceId = sourceId;
        this.ratePerSecond = ratePerSecond;
    }

    public String getSourceId() {
        return sourceId;
    }

    public float getRatePerSecond() {
        return ratePerSecond;
    }

    public String contributionKey() {
        return CONTRIBUTION_KEY_PREFIX + sourceId;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "modify_primalstinct_rate"),
                new SerializableData()
                        .add("source_id", SerializableDataTypes.STRING, null)
                        .add("rate_per_second", SerializableDataTypes.FLOAT, 0.0f),
                data -> (powerType, livingEntity) -> new ModifyPrimalstinctRatePower(
                        powerType,
                        livingEntity,
                        data.isPresent("source_id") ? data.getString("source_id") : "unnamed",
                        data.getFloat("rate_per_second"))
        ).allowCondition();
    }
}
