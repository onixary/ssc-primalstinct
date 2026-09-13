package net.onixary.sscPrimalstinct.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/** Vanilla timed effect: milk and explicit removal cancel the call. */
public final class PrimalCallEffect extends StatusEffect {
    public static final StatusEffect INSTANCE = new PrimalCallEffect();

    private PrimalCallEffect() {
        super(StatusEffectCategory.NEUTRAL, 0xA53854);
    }

    public static void register() {
        Registry.register(Registries.STATUS_EFFECT,
                Identifier.of(SSCPrimalstinct.MOD_ID, "primal_call"), INSTANCE);
    }
}
