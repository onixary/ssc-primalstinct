package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlock;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlockEntity;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import net.onixary.sscPrimalstinct.items.RegPrimalstinctItems;

/** 已激活祭坛的奖励预览，不生成可拾取实体；领取或断电后随方块状态消失。 */
public final class PrimalAltarRenderer extends EndgameGeoBlockRenderer<PrimalAltarBlockEntity> {
    private final ItemRenderer itemRenderer;
    private final ItemStack fragment = new ItemStack(RegPrimalstinctItems.SEDATIVE_FRAGMENT);

    public PrimalAltarRenderer(BlockEntityRendererFactory.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void renderFinal(MatrixStack matrices, PrimalAltarBlockEntity entity, BakedGeoModel model,
                            VertexConsumerProvider consumers, VertexConsumer buffer, float tickDelta,
                            int light, int overlay, float red, float green, float blue, float alpha) {
        var state = entity.getCachedState();
        if (entity.getWorld() == null || !state.isOf(RegEndgameBlocks.PRIMAL_ALTAR)
                || !state.get(PrimalAltarBlock.ACTIVE)) {
            return;
        }
        double ticks = (entity.getWorld().getTime() % 24000L) + tickDelta;
        matrices.push();
        matrices.translate(0.5, 1.55 + Math.sin(ticks * Math.PI / 40.0) * 0.08, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (ticks * 2.0 % 360.0)));
        matrices.scale(0.65f, 0.65f, 0.65f);
        itemRenderer.renderItem(fragment, ModelTransformationMode.GROUND,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, matrices, consumers,
                entity.getWorld(), (int) entity.getPos().asLong());
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(PrimalAltarBlockEntity entity) {
        return true;
    }
}
