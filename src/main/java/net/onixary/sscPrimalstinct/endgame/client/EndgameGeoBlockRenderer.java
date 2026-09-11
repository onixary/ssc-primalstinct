package net.onixary.sscPrimalstinct.endgame.client;

import net.onixary.sscPrimalstinct.endgame.block.EndgameGeoBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class EndgameGeoBlockRenderer<T extends EndgameGeoBlockEntity> extends GeoBlockRenderer<T> {
    public EndgameGeoBlockRenderer() {
        this(new EndgameGeoModel<>(EndgameGeoBlockEntity::getCachedState));
    }

    private EndgameGeoBlockRenderer(EndgameGeoModel<T> model) {
        super(model);
        addRenderLayer(new EndgameEmissionLayer<>(this, model));
    }
}
