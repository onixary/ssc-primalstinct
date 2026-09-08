package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * ssc-primalstinct:passive_until_provoked —— 生物不主动仇恨持有者，被打后才仇恨反击。
 * 实现：TargetPredicateMixin 在 TargetPredicate.test 头部注入——判定目标为持有本 Power
 * 的玩家、且该生物的 lastAttacker 不是该玩家时直接返回 false。这会挡掉所有以
 * TargetPredicate 主动选取玩家的 AI（含原版 ActiveTargetGoal 的 getClosestPlayer 路径、
 * 幻翼/末影人/蜜蜂等自建谓词，以及模组生物）；RevengeGoal 的还击同样经过此判定，
 * 但受击时 lastAttacker 已指向攻击者，放行——"主动不仇恨、受击才仇恨"由此成立。
 * 受击个体的仇恨维持走 TrackTargetGoal.shouldContinue（不复查本判定），按原版
 * 距离/视线丢失条件自然脱战。脑 AI（如监守者）与直接 setTarget 的特殊路径不受影响。
 */
public class PassiveUntilProvokedPower extends Power {

    public PassiveUntilProvokedPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "passive_until_provoked"),
                new SerializableData(),
                data -> (type, entity) -> new PassiveUntilProvokedPower(type, entity)
        ).allowCondition();
    }
}
