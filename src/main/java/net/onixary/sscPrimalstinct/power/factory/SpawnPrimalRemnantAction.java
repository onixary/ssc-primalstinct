package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.service.RemnantService;

/**
 * 眷属实现07：ssc-primalstinct:spawn_primal_remnant —— 击杀触发原初残余的实体动作。
 * 挂载方式：数据包 power 用 apoli:self_action_on_kill（本地 Apoli 2.9.2 支持的击杀触发能力），
 * 目标白名单（“敌人”实体标签，默认 c:hostiles）由 power 的 target_condition 数据化配置，
 * 直接/投射物归属由 Apoli 击杀事件的伤害来源归因处理（宠物击杀与 PVP 不在白名单内）。
 * 资格与概率不在此配置：动作进入 RemnantService 再次服务端核验（名单内形态+实际最高级），
 * 并按 endgame/rituals 数据包的 remnant_drop_chance 统一只掷一次随机。
 */
public final class SpawnPrimalRemnantAction {

    private SpawnPrimalRemnantAction() {
    }

    public static void action(SerializableData.Instance data, Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        RemnantService.tryLaunchFromKill(player);
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                net.onixary.sscPrimalstinct.endgame.EndgameRules.id("spawn_primal_remnant"),
                new SerializableData(),
                SpawnPrimalRemnantAction::action
        );
    }
}
