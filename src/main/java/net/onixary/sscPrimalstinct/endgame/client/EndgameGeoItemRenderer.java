package net.onixary.sscPrimalstinct.endgame.client;

import net.onixary.sscPrimalstinct.endgame.block.EndgameGeoBlockItem;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class EndgameGeoItemRenderer extends GeoItemRenderer<EndgameGeoBlockItem> {
    public EndgameGeoItemRenderer() {
        this(new EndgameGeoModel<>(item -> item.getBlock().getDefaultState()));
    }

    private EndgameGeoItemRenderer(EndgameGeoModel<EndgameGeoBlockItem> model) {
        super(model);
        addRenderLayer(new EndgameEmissionLayer<>(this, model));
    }

    @Override
    public void preRender(MatrixStack matrices, EndgameGeoBlockItem item, BakedGeoModel model,
                          VertexConsumerProvider consumers, VertexConsumer buffer, boolean isReRender,
                          float tickDelta, int light, int overlay, float red, float green, float blue, float alpha) {
        super.preRender(matrices, item, model, consumers, buffer, isReRender, tickDelta, light, overlay,
                red, green, blue, alpha);
        // Shared block geometry has its floor at y=0; GeoItemRenderer assumes an origin-centred item.
        if (!isReRender) matrices.translate(0, -0.51f, 0);
    }
}
