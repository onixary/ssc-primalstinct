package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.endgame.service.RitualPortalService;

/**
 * 眷属实现03/08：原初传送门框架方块（末地传送门式 5×5 环，去四角共 12 块；缺角不影响判定）。
 * 破框时移除关联门面并置 BROKEN——避免孤立传送格；修框后再次开门需要新碎片（默认规则）。
 */
public class PrimalPortalFrameBlock extends Block {

    public PrimalPortalFrameBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
            RitualPortalService.breakPortalAround(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public static Settings frameSettings() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN).strength(50.0f, 1200.0f);
    }
}
