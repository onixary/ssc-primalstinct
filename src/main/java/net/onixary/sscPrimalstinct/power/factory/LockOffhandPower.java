package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡10：ssc-primalstinct:lock_offhand —— 副手槽（40）锁定。
 * 首次生效/再次装备：清空原槽与原地掉落成对且只执行一次（短拾取延迟防循环）；Trinkets 不在范围。
 */
public class LockOffhandPower extends Power {
    public LockOffhandPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "lock_offhand"),
                new SerializableData(),
                data -> LockOffhandPower::new
        ).allowCondition();
    }
}
