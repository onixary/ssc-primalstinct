package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡10：prevent_containers —— 禁用容器方块（箱子/ trapped箱/末影箱/木桶/潜影盒/漏斗/发射器/投掷器）
 * 客户端预测与服务端权威双拒绝，右键不弹界面。
 */
public class PreventContainersPower extends Power {
    public PreventContainersPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "prevent_containers"),
                new SerializableData(),
                data -> PreventContainersPower::new
        ).allowCondition();
    }
}
