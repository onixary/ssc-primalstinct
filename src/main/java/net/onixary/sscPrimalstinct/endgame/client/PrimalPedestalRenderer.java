package net.onixary.sscPrimalstinct.endgame.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPedestalBlockEntity;

/**
 * 眷属实现03：原初基座供物图标 BER。
 * 需求物图标同时挂在基座四个水平面上（类似四个物品展示框），任意方向走近都能看到需求，
 * 不依赖 FACING；完成后图标变暗（fulfilled 视觉）。不生成可被打落的真实 ItemFrame。
 * 仅物理客户端注册（专用服务器不加载 renderer）。
 */
public class PrimalPedestalRenderer implements BlockEntityRenderer<PrimalPedestalBlockEntity> {

    public PrimalPedestalRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(PrimalPedestalBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        Identifier itemId = entity.getRequirementItem();
        if (itemId == null || !Registries.ITEM.containsId(itemId)) {
            return;
        }
        ItemStack stack = new ItemStack(Registries.ITEM.get(itemId), 1);
        if (stack.isOf(Items.AIR)) {
            return;
        }
        boolean fulfilled = entity.isFulfilled();
        double time = (System.currentTimeMillis() % 6283L) / 1000.0;
        float bob = fulfilled ? 0.0f : (float) Math.sin(time * 2.0) * 0.02f;
        int renderLight = fulfilled ? Math.max(4, light * 3 / 8) : light;

        for (Direction facing : Direction.Type.HORIZONTAL) {
            matrices.push();
            // 图标贴在基座该侧面表面：沿 FACING 向外偏移 0.52，悬在台面上部（10/16 高度附近）
            Vec3d offset = Vec3d.of(facing.getVector()).multiply(0.52);
            matrices.translate(0.5 + offset.x, 0.62 + bob, 0.5 + offset.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing.asRotation()));
            matrices.scale(0.5f, 0.5f, 0.5f);
            MinecraftClient.getInstance().getItemRenderer().renderItem(stack,
                    ModelTransformationMode.FIXED, renderLight, overlay, matrices, vertexConsumers,
                    entity.getWorld(), (int) entity.getPos().asLong());
            matrices.pop();
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(PrimalPedestalBlockEntity entity) {
        return false;
    }
}
