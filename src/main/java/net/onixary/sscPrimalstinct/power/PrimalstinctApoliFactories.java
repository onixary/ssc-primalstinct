package net.onixary.sscPrimalstinct.power;

import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.power.factory.condition.ConditionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registry;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.power.factory.AddPrimalstinctAction;
import net.onixary.sscPrimalstinct.power.factory.ModifyPrimalstinctRatePower;
import net.onixary.sscPrimalstinct.power.factory.PrimalstinctConditions;
import net.onixary.sscPrimalstinct.power.factory.RestrictHotbarPower;
import net.onixary.sscPrimalstinct.power.factory.RestrictInventoryPower;

/**
 * 卡07：Apoli 工厂注册（与 SSC AdditionalPowers/AdditionalEntityActions 同模式）。
 * 少量通用工厂承载所有形态的本能事件，数值留在 JSON。
 */
public final class PrimalstinctApoliFactories {

    private PrimalstinctApoliFactories() {
    }

    public static void register() {
        Registry.register(ApoliRegistries.POWER_FACTORY,
                ModifyPrimalstinctRatePower.getFactory().getSerializerId(),
                ModifyPrimalstinctRatePower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                RestrictHotbarPower.getFactory().getSerializerId(),
                RestrictHotbarPower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                RestrictInventoryPower.getFactory().getSerializerId(),
                RestrictInventoryPower.getFactory());
        Registry.register(ApoliRegistries.ENTITY_ACTION,
                AddPrimalstinctAction.getFactory().getSerializerId(),
                AddPrimalstinctAction.getFactory());
        Registry.register(ApoliRegistries.ENTITY_CONDITION,
                PrimalstinctConditions.valueCondition().getSerializerId(),
                PrimalstinctConditions.valueCondition());
        Registry.register(ApoliRegistries.ENTITY_CONDITION,
                PrimalstinctConditions.levelCondition().getSerializerId(),
                PrimalstinctConditions.levelCondition());
        Registry.register(ApoliRegistries.ENTITY_CONDITION,
                PrimalstinctConditions.lockedCondition().getSerializerId(),
                PrimalstinctConditions.lockedCondition());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.LockOffhandPower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.LockOffhandPower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.PreventInventoryCraftingPower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.PreventInventoryCraftingPower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.PreventDoorPower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.PreventDoorPower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.PreventBlockPlacePower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.PreventBlockPlacePower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.DropToolAfterUsePower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.DropToolAfterUsePower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.PreventProcessingBlocksPower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.PreventProcessingBlocksPower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.PreventContainersPower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.PreventContainersPower.getFactory());
        Registry.register(ApoliRegistries.POWER_FACTORY,
                net.onixary.sscPrimalstinct.power.factory.CurlSleepPower.getFactory().getSerializerId(),
                net.onixary.sscPrimalstinct.power.factory.CurlSleepPower.getFactory());
        SSCPrimalstinct.LOGGER.info("[primalstinct] apoli factories registered");
    }
}
