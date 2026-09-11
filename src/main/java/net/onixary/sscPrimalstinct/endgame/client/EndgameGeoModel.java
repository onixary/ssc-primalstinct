package net.onixary.sscPrimalstinct.endgame.client;

import java.util.function.Function;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

public final class EndgameGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final Function<T, BlockState> state;

    public EndgameGeoModel(Function<T, BlockState> state) {
        this.state = state;
    }

    public EndgameBlockVisual visual(T animatable) {
        return EndgameBlockVisual.from(state.apply(animatable));
    }

    @Override
    public Identifier getModelResource(T animatable) {
        return visual(animatable).model;
    }

    @Override
    public Identifier getTextureResource(T animatable) {
        return visual(animatable).texture;
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return visual(animatable).animation;
    }

    @Override
    public RenderLayer getRenderType(T animatable, Identifier texture) {
        return visual(animatable) == EndgameBlockVisual.PORTAL
                ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture);
    }
}
