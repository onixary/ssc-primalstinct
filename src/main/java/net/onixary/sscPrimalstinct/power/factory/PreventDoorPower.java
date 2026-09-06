package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡10：ssc-primalstinct:prevent_door —— 禁用门/活板门/栅栏门（覆盖双手）。
 * 床保持可用（参照 true-feral-addon-modifier 的床例外）。被本规则拒绝不算成功使用。
 */
public class PreventDoorPower extends Power {
    public PreventDoorPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "prevent_door"),
                new SerializableData(),
                data -> PreventDoorPower::new
        ).allowCondition();
    }
}
