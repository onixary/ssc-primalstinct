package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡10：ssc-primalstinct:drop_tool_after_use —— 成功行为后原地掉落手持工具/武器。
 * 触发面：武器命中、铲地/剥皮/耕地成功（useOnBlock SUCCESS）、方块真正破坏成功；
 * 失败攻击/空气挥动/挖掘开始或取消不触发。掉落的是真实剩余堆栈（含耐久变化），
 * 行为开始时记录手/槽/堆栈身份，防切槽丢错物品；单次动作单次掉落（各钩子互不重叠）。
 * 动作钩子：on_drop_action（实际掉落时）/ on_keep_action（成功行为但掷骰未掉落时）。
 */
public class DropToolAfterUsePower extends Power {
    public final float chance;
    public final boolean attack;
    public final ActionFactory<Entity>.Instance onDropAction;
    public final ActionFactory<Entity>.Instance onKeepAction;
    public DropToolAfterUsePower(PowerType<?> type, LivingEntity entity, float chance, boolean attack,
                                 ActionFactory<Entity>.Instance onDropAction,
                                 ActionFactory<Entity>.Instance onKeepAction) {
        super(type, entity);
        this.chance = Float.isFinite(chance) ? Math.max(0, Math.min(1, chance)) : 0;
        this.attack = attack;
        this.onDropAction = onDropAction;
        this.onKeepAction = onKeepAction;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "drop_tool_after_use"),
                new SerializableData().add("chance", SerializableDataTypes.FLOAT, 1.0f)
                        .add("on_attack", SerializableDataTypes.BOOLEAN, true)
                        .add("on_drop_action", ApoliDataTypes.ENTITY_ACTION, null)
                        .add("on_keep_action", ApoliDataTypes.ENTITY_ACTION, null),
                data -> (type, entity) -> new DropToolAfterUsePower(type, entity,
                        data.getFloat("chance"), data.getBoolean("on_attack"),
                        data.get("on_drop_action"), data.get("on_keep_action"))
        ).allowCondition();
    }
}
