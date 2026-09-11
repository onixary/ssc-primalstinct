package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;

/**
 * 眷属实现08：原初传送门门面 BE（渲染专用，无数据）。
 * 继承 EndPortalBlockEntity 以复用原版末地门渲染（EndPortalBlockEntityRenderer +
 * rendertype_end_portal 着色器动画与 shouldDrawSide 的 Y 轴面判定）；
 * 方块自身模型设为 INVISIBLE，世界内只显示末地门星野效果。
 */
public class PrimalPortalBlockEntity extends EndPortalBlockEntity {

    public static final Identifier BLOCK_ENTITY_ID = EndgameRules.id("primal_portal");

    public PrimalPortalBlockEntity(BlockPos pos, BlockState state) {
        super(RegEndgameBlockEntities.PRIMAL_PORTAL, pos, state);
    }
}
