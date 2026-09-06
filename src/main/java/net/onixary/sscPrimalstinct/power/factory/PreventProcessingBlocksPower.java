package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡10：prevent_processing_blocks —— 禁用原版物品加工方块（工作台/熔炉/高炉/烟熏炉/酿造台/切石机/织布机/制图台/砂轮/锻造台/铁砧）
 * 客户端预测与服务端权威双拒绝，右键不弹界面。
 */
public class PreventProcessingBlocksPower extends Power {
    public PreventProcessingBlocksPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "prevent_processing_blocks"),
                new SerializableData(),
                data -> PreventProcessingBlocksPower::new
        ).allowCondition();
    }
}
