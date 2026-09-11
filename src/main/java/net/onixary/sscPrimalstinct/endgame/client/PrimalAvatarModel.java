package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.entity.PrimalAvatarEntity;
import software.bernie.geckolib.model.GeoModel;

/** 眷属实现11：原始化身 GeoModel（占位资源，正式模型后续替换）。 */
public class PrimalAvatarModel extends GeoModel<PrimalAvatarEntity> {

    @Override
    public Identifier getModelResource(PrimalAvatarEntity animatable) {
        return EndgameRules.id("geo/entity/primal_avatar.geo.json");
    }

    @Override
    public Identifier getTextureResource(PrimalAvatarEntity animatable) {
        return EndgameRules.id("textures/entity/primal_avatar.png");
    }

    @Override
    public Identifier getAnimationResource(PrimalAvatarEntity animatable) {
        return EndgameRules.id("animations/primal_avatar.animation.json");
    }
}
