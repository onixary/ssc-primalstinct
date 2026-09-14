package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;

/** Beds retain their respawn function but cannot start sleep. */
public class BedSpawnOnlyPower extends Power {
    public BedSpawnOnlyPower(PowerType<?> type, LivingEntity entity) { super(type, entity); }

    public static boolean applies(PlayerEntity player) {
        return PrimalstinctLifecycle.isManaged(player)
                && PowerHolderComponent.getPowers(player, BedSpawnOnlyPower.class).stream().anyMatch(Power::isActive);
    }

    public static PowerFactory<?> getFactory() {
        return new PowerFactory<>(Identifier.of(SSCPrimalstinct.MOD_ID, "bed_spawn_only"),
                new SerializableData(), data -> BedSpawnOnlyPower::new).allowCondition();
    }
}
