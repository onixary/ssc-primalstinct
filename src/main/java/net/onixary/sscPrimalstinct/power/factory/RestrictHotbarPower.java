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
 * 卡09：ssc-primalstinct:restrict_hotbar —— 快捷栏可用槽位数（0–9）。
 * 与 restrict_inventory 并存时按 max 合并；规则变化由 InventoryLockManager 处理搬移/暂存。
 * 支持条件（PowerFactory.allowCondition）。
 */
public class RestrictHotbarPower extends Power {

    private final int allowedSlots;

    public RestrictHotbarPower(PowerType<?> type, LivingEntity entity, int allowedSlots) {
        super(type, entity);
        this.allowedSlots = allowedSlots;
    }

    public int getAllowedSlots() {
        return allowedSlots;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "restrict_hotbar"),
                new SerializableData()
                        .add("allowed_slots", SerializableDataTypes.INT, 0),
                data -> (powerType, livingEntity) -> new RestrictHotbarPower(
                        powerType, livingEntity, Math.max(0, data.getInt("allowed_slots")))
        ).allowCondition();
    }
}
