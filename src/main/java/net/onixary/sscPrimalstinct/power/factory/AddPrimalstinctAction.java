package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctService;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctSource;

/**
 * 卡07：ssc-primalstinct:add_primalstinct —— 即时改值 entity action。
 * 字段：amount；统一走 POWER 来源（满值锁定时按内核规则拒绝，JSON 不可绕）。
 * 只应挂在真实事件（吃完/受伤/击杀等）的回调上，不挂在"获得 Power"上，防止重登刷值。
 * 注意：ActionFactory 不套用 allowCondition（仅 PowerFactory 支持，卡07 明确约束）。
 */
public final class AddPrimalstinctAction {

    private AddPrimalstinctAction() {
    }

    public static void action(SerializableData.Instance data, Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        float amount = data.getFloat("amount");
        boolean applied = PrimalstinctService.modify(player, PrimalstinctSource.POWER, amount);
        if (!applied && SSCPrimalstinct.isDevelopmentEnvironment()) {
            SSCPrimalstinct.LOGGER.debug("[primalstinct] add_primalstinct amount={} 被拒绝（玩家 {}，锁定/规则）",
                    amount, player.getGameProfile().getName());
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "add_primalstinct"),
                new SerializableData()
                        .add("amount", SerializableDataTypes.FLOAT, 0.0f),
                AddPrimalstinctAction::action
        );
    }
}
