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
import net.onixary.sscPrimalstinct.instinct.PrimalstinctService;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctSource;

/**
 * 卡14：镇静碎片——满值后的可获得恢复手段。
 * 使用后消耗并恢复（降低）25 点本能值，走 ITEM_RECOVERY 来源（满值锁定的唯一合法解除路径）。
 * 数值为首轮流价值（卡18 再平衡）。
 */
public class SedativeFragmentItem extends Item {

    public static final float RECOVERY_AMOUNT = 25.0f;

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

        // ITEM_RECOVERY：满值锁定下唯一允许的负向修改来源
        boolean applied = PrimalstinctService.modify(player, PrimalstinctSource.ITEM_RECOVERY, -RECOVERY_AMOUNT);
        if (applied) {
            if (!player.isCreative()) { stack.decrement(1); }
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.2f);
            player.sendMessage(Text.translatable("ssc-primalstinct.item.sedative_fragment.used",
                    RECOVERY_AMOUNT), true);
            SSCPrimalstinct.LOGGER.debug("[primalstinct] {} used sedative fragment (-{})",
                    player.getGameProfile().getName(), RECOVERY_AMOUNT);
            return TypedActionResult.consume(stack);
        }
        // 消耗失败不降值（但物品不消耗）
        player.sendMessage(Text.translatable("ssc-primalstinct.item.sedative_fragment.no_effect"), true);
        return TypedActionResult.fail(stack);
    }
}
