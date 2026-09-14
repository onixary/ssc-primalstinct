package net.onixary.sscPrimalstinct.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;

/**
 * 卡14→眷属实现07 调整（2026-09-14 用户决策）：
 * 镇静碎片——退出原始本能系统，回到 SSC 原版本能逻辑。
 * 使用条件：已觉醒（primalAwakened）；未觉醒使用无作用且不消耗。
 * 退出管线：清觉醒标记与数值 → 管理权切换（速率/游荡/蜷缩清理、Power 结算、HUD 同步、
 * 库存规则放宽归还暂存物品）→ 重建形态 → SSC 本能条清零。
 * 永久形态无需特判：SSC 原版逻辑对 NoInstinct/LockInstinct 旗帜形态自动保持本能条禁用。
 */
public class SedativeFragmentItem extends Item {

    public SedativeFragmentItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        var component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
        if (!component.isPrimalAwakened()) {
            player.sendMessage(Text.translatable("ssc-primalstinct.item.sedative_fragment.no_effect"), true);
            return TypedActionResult.fail(stack);
        }

        component.exitPrimalSystem();
        // 管理权切换管线：清速率/游荡/蜷缩，结算移除附属 Power，库存规则放宽（暂存物品掉回），同步 HUD
        PrimalstinctLifecycle.refresh(player);
        SSCAdapter.rebuildCurrentForm(player);
        // SSC 原版本能条清零：从干净状态回到原版逻辑（原版 tick 对永久形态自动维持禁用）
        net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils.clearInstinct(player);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 0.6f);
        player.sendMessage(Text.translatable("ssc-primalstinct.item.sedative_fragment.used"), false);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 使用镇静碎片，退出原始本能系统（回到原版逻辑）",
                player.getGameProfile().getName());
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        return TypedActionResult.consume(stack);
    }
}
