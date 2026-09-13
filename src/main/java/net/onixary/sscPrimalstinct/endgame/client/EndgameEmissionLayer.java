package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import java.util.function.Function;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Alpha-masked fullbright pass using the artist's *_emission.png without renaming or flattening it. */
public final class EndgameEmissionLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private final Function<T, Identifier> emissionTexture;

    public EndgameEmissionLayer(GeoRenderer<T> renderer, EndgameGeoModel<T> model) {
        this(renderer, animatable -> model.visual(animatable).emission);
    }

    public EndgameEmissionLayer(GeoRenderer<T> renderer, Function<T, Identifier> emissionTexture) {
        super(renderer);
        this.emissionTexture = emissionTexture;
    }

    @Override
    public void render(MatrixStack matrices, T animatable, BakedGeoModel bakedModel, RenderLayer renderType,
                       VertexConsumerProvider consumers, VertexConsumer buffer, float tickDelta,
                       int light, int overlay) {
        var emission = emissionTexture.apply(animatable);
        // Resource-manager lookup also handles F3+T/resource-pack changes; no stale negative cache.
        if (MinecraftClient.getInstance().getResourceManager().getResource(emission).isEmpty()) {
            return;
        }
        RenderLayer layer = RenderLayer.getEntityTranslucentEmissive(emission);
        getRenderer().reRender(bakedModel, matrices, consumers, animatable, layer, consumers.getBuffer(layer),
                tickDelta, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 1, 1, 1, 1);
    }
}
