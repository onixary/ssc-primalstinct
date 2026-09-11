package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPortalBlockEntity;

/**
 * 眷属实现08：原初传送门渲染器。
 * 复用原版末地门星野效果（rendertype_end_portal 着色器 + Y 轴面），
 * 将门面表面下移至原版高度的一半（顶面 0.75→0.375，底面等比 0.375→0.1875），
 * 视觉上更深地沉入地坑，与 5×5 门框的包边层次更贴合。
 */
public class PrimalPortalRenderer extends EndPortalBlockEntityRenderer<PrimalPortalBlockEntity> {

    public PrimalPortalRenderer(BlockEntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    protected float getTopYOffset() {
        return 0.375F;
    }

    @Override
    protected float getBottomYOffset() {
        return 0.1875F;
    }
}
