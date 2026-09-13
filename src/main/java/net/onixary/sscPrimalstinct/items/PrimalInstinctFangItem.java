package net.onixary.sscPrimalstinct.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.instinct.PrimalCallService;

public final class PrimalInstinctFangItem extends Item {
    public PrimalInstinctFangItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player) || !PrimalCallService.apply(player)) {
            user.sendMessage(Text.translatable("item.ssc-primalstinct.primal_instinct_fang.unavailable"), true);
            return TypedActionResult.fail(stack);
        }
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.consume(stack);
    }
}
