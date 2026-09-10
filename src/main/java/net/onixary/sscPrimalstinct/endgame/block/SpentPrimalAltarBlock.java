package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * 眷属实现03/06：失效的原初祭坛（独立方块，不能仅靠 ACTIVE=false 模拟）。
 * 右键永远不产出；SPENT 不回退，任何掉落/克隆路径不得把它还原为可领取祭坛。
 */
public class SpentPrimalAltarBlock extends Block {

    public SpentPrimalAltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        return ActionResult.PASS;
    }

    public static Settings spentSettings() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f);
    }
}
