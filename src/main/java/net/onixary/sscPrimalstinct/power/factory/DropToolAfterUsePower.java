package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡10：ssc-primalstinct:drop_tool_after_use —— 成功行为后原地掉落手持工具/武器。
 * 触发面：武器命中、铲地/剥皮/耕地成功（useOnBlock SUCCESS）、方块真正破坏成功；
 * 失败攻击/空气挥动/挖掘开始或取消不触发。掉落的是真实剩余堆栈（含耐久变化），
 * 行为开始时记录手/槽/堆栈身份，防切槽丢错物品；单次动作单次掉落（各钩子互不重叠）。
 */
public class DropToolAfterUsePower extends Power {
    public DropToolAfterUsePower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "drop_tool_after_use"),
                new SerializableData(),
                data -> DropToolAfterUsePower::new
        ).allowCondition();
    }
}
