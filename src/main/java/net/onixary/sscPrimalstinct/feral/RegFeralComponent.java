package net.onixary.sscPrimalstinct.feral;

import dev.onyxstudios.cca.api.v3.component.*;
import dev.onyxstudios.cca.api.v3.entity.*;
import net.minecraft.util.Identifier;

public final class RegFeralComponent implements EntityComponentInitializer {
    public static final ComponentKey<FeralComponent> FERAL = ComponentRegistry.getOrCreate(
            new Identifier("ssc-primalstinct", "feral_presentation"), FeralComponent.class);
    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(FERAL, FeralComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
