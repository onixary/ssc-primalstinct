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
 * 卡11：ssc-primalstinct:curl_sleep —— 蜷缩与无床睡眠。
 * 字段：allow_sleep（夜间计入睡眠统计）；instinct_rate_while_sleeping（睡眠期本能速率，点/秒，POWER 来源；负值=降低）。
 * 激活方式：附属命名空间主动键（C2S 请求，服务端权威校验）。
 */
public class CurlSleepPower extends Power {

    private final boolean allowSleep;
    private final float instinctRateWhileSleeping;

    public CurlSleepPower(PowerType<?> type, LivingEntity entity,
                          boolean allowSleep, float instinctRateWhileSleeping) {
        super(type, entity);
        this.allowSleep = allowSleep;
        this.instinctRateWhileSleeping = instinctRateWhileSleeping;
    }

    public boolean isAllowSleep() {
        return allowSleep;
    }

    public float getInstinctRateWhileSleeping() {
        return instinctRateWhileSleeping;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "curl_sleep"),
                new SerializableData()
                        .add("allow_sleep", SerializableDataTypes.BOOLEAN, true)
                        .add("instinct_rate_while_sleeping", SerializableDataTypes.FLOAT, -0.1f),
                data -> (powerType, livingEntity) -> new CurlSleepPower(
                        powerType, livingEntity,
                        data.getBoolean("allow_sleep"),
                        data.getFloat("instinct_rate_while_sleeping"))
        ).allowCondition();
    }
}
