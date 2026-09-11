package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/** Older saves have no block-entity NBT for the four formerly static block types. */
final class EndgameModelMigration {
    private static boolean registered;

    private EndgameModelMigration() {
    }

    static void register() {
        if (registered) return;
        registered = true;
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> populateMissingEntities(chunk));
    }

    private static void populateMissingEntities(WorldChunk chunk) {
        var sections = chunk.getSectionArray();
        var pos = new BlockPos.Mutable();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (section.isEmpty() || !section.hasAny(state -> state.getBlock() instanceof EndgameModelBlock)) continue;
            int baseY = (chunk.getBottomSectionCoord() + sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!(section.getBlockState(x, y, z).getBlock() instanceof EndgameModelBlock)) continue;
                        pos.set(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
                        if (chunk.getBlockEntity(pos, WorldChunk.CreationType.CHECK) == null
                                && chunk.getBlockEntity(pos, WorldChunk.CreationType.IMMEDIATE) != null) {
                            chunk.setNeedsSaving(true);
                        }
                    }
                }
            }
        }
    }
}
