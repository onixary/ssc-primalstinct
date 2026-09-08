package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 卡09 UI 反馈（MIXIN_INVENTORY.md V1-client）：True Feral 式紧凑快捷栏——
 * 受限时只绘制允许槽位（裁剪 widgets.png 左段 N*20+2 宽 + 选择框映射到紧凑坐标），
 * 被禁栏位不再渲染；配合 PlayerInventoryClientMixin 的滚轮跳过，消除高亮跳变。
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    private static final Identifier PRIMALSTINCT_WIDGETS = new Identifier("textures/gui/widgets.png");

    @Inject(method = "render", at = @At("HEAD"))
    private void primalstinct$renderTakeoverOverlay(DrawContext context, float tickDelta, CallbackInfo ci) {
        net.onixary.sscPrimalstinct.client.effect.WanderTakeoverOverlay.render(context);
    }

    @Shadow
    private void renderHotbarItem(DrawContext context, int x, int y, float tickDelta,
                                   PlayerEntity player, ItemStack stack, int seed) {
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void primalstinct$renderCompactHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        if (!ClientPrimalstinctState.inventoryRestricted()) {
            return;  // 未受限走原版
        }
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || player.isSpectator()) {
            // 原版在 hudHidden/spectator 时也不绘制；交还原版处理其空逻辑
            ci.cancel();
            return;
        }
        int allowed = Math.max(0, Math.min(9, ClientPrimalstinctState.allowedHotbar()));
        if (allowed == 0) {
            ci.cancel();  // 无可用槽：不绘制快捷栏
            return;
        }
        ci.cancel();
        int barWidth = allowed * 20 + 2;
        int x = client.getWindow().getScaledWidth() / 2 - barWidth / 2;
        int y = client.getWindow().getScaledHeight() - 22;
        context.drawTexture(PRIMALSTINCT_WIDGETS, x, y, 0, 0, barWidth, 22);
        for (int i = 0; i < allowed; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                // 原版锚点：X = barX + i*20 + 3；Y = barY + 3（scaledHeight-16-3）
                this.renderHotbarItem(context, x + 3 + i * 20, y + 3, tickDelta, player, stack, i + 1);
            }
        }
        int selected = player.getInventory().selectedSlot;
        if (selected >= 0 && selected < allowed) {
            context.drawTexture(PRIMALSTINCT_WIDGETS, x - 1 + selected * 20, y - 1, 0, 22, 24, 23);
        }
    }
}
