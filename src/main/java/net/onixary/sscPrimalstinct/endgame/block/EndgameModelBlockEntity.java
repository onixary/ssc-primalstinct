package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Render-only instance for wires, spent altars and portal blocks. */
public final class EndgameModelBlockEntity extends EndgameGeoBlockEntity {
    public EndgameModelBlockEntity(BlockPos pos, BlockState state) {
        super(RegEndgameBlockEntities.ENDGAME_MODEL, pos, state);
    }
}
