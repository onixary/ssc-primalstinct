package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.entity.PrimalAvatarEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 眷属实现11：原始化身渲染器（无阴影；嵌入方块的纯演出实体）。 */
public class PrimalAvatarRenderer extends GeoEntityRenderer<PrimalAvatarEntity> {
    private static final Identifier EMISSION_TEXTURE =
            EndgameRules.id("textures/entity/primal_avatar_emission.png");

    public PrimalAvatarRenderer(EntityRendererFactory.Context context) {
        super(context, new PrimalAvatarModel());
        addRenderLayer(new EndgameEmissionLayer<>(this, avatar -> EMISSION_TEXTURE));
        this.shadowRadius = 0.0f;
    }
}
