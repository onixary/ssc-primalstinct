package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.onixary.sscPrimalstinct.endgame.entity.PrimalAvatarEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 眷属实现11：原始化身渲染器（无阴影；嵌入方块的纯演出实体）。 */
public class PrimalAvatarRenderer extends GeoEntityRenderer<PrimalAvatarEntity> {

    public PrimalAvatarRenderer(EntityRendererFactory.Context context) {
        super(context, new PrimalAvatarModel());
        this.shadowRadius = 0.0f;
    }
}
