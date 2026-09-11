package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlock;
import net.onixary.sscPrimalstinct.endgame.block.PrimalEnergyWireBlock;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPedestalBlock;

/** One resource set per visible block state, shared by placed blocks and their items. */
public enum EndgameBlockVisual {
    PEDESTAL("primal_pedestal", "primal_pedestal_moss"),
    PEDESTAL_FULFILLED("primal_pedestal_fulfilled", "primal_pedestal_moss_fulfilled"),
    ALTAR("primal_altar"),
    ALTAR_ACTIVE("primal_altar_active"),
    WIRE("primal_energy_wire"),
    WIRE_LIT("primal_energy_wire_lit"),
    SPENT_ALTAR("spent_primal_altar"),
    PORTAL_FRAME("primal_portal_frame"),
    PORTAL("primal_portal"),
    CONVERSION_PLATFORM("primal_conversion_platform");

    public final Identifier model;
    public final Identifier texture;
    public final Identifier emission;
    public final Identifier animation;

    EndgameBlockVisual(String name) {
        this(name, "gecko/" + name);
    }

    EndgameBlockVisual(String name, String textureName) {
        model = EndgameRules.id("geo/block/" + name + ".geo.json");
        texture = EndgameRules.id("textures/block/" + textureName + ".png");
        emission = EndgameRules.id("textures/block/" + textureName + "_emission.png");
        animation = EndgameRules.id("animations/block/" + name + ".animation.json");
    }

    public static EndgameBlockVisual from(BlockState state) {
        return switch (Registries.BLOCK.getId(state.getBlock()).getPath()) {
            case "primal_pedestal" -> state.get(PrimalPedestalBlock.FULFILLED) ? PEDESTAL_FULFILLED : PEDESTAL;
            case "primal_altar" -> state.get(PrimalAltarBlock.ACTIVE) ? ALTAR_ACTIVE : ALTAR;
            case "primal_energy_wire" -> state.get(PrimalEnergyWireBlock.LIT) ? WIRE_LIT : WIRE;
            case "spent_primal_altar" -> SPENT_ALTAR;
            case "primal_portal_frame" -> PORTAL_FRAME;
            case "primal_portal" -> PORTAL;
            case "primal_conversion_platform" -> CONVERSION_PLATFORM;
            default -> throw new IllegalArgumentException("Unsupported endgame block: " + state);
        };
    }
}
