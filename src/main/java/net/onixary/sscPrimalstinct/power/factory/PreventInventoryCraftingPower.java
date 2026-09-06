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
 * 卡10：ssc-primalstinct:prevent_inventory_crafting —— 仅禁玩家背包 2×2 合成
 * （结果提取/快捷合成/配方书转移）。include_crafting_table=true 时才连带禁工作台 3×3，
 * 两者不因类名相近一起禁。
 */
public class PreventInventoryCraftingPower extends Power {

    private final boolean includeCraftingTable;

    public PreventInventoryCraftingPower(PowerType<?> type, LivingEntity entity, boolean includeCraftingTable) {
        super(type, entity);
        this.includeCraftingTable = includeCraftingTable;
    }

    public boolean includesCraftingTable() {
        return includeCraftingTable;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "prevent_inventory_crafting"),
                new SerializableData()
                        .add("include_crafting_table", SerializableDataTypes.BOOLEAN, false),
                data -> (powerType, livingEntity) -> new PreventInventoryCraftingPower(
                        powerType, livingEntity, data.getBoolean("include_crafting_table"))
        ).allowCondition();
    }
}
