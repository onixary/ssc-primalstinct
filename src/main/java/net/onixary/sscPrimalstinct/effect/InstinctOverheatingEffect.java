package net.onixary.sscPrimalstinct.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/** Marker only: no ticking effects or attribute modifiers. */
public final class InstinctOverheatingEffect extends StatusEffect {
    public static final StatusEffect INSTANCE = new InstinctOverheatingEffect();
    private InstinctOverheatingEffect() {
        super(StatusEffectCategory.NEUTRAL, 0xE66B32);
    }
    public static void register() {
        Registry.register(Registries.STATUS_EFFECT,
                Identifier.of(SSCPrimalstinct.MOD_ID, "instinct_overheating"), INSTANCE);
    }
}
