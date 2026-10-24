package net.onixary.sscPrimalstinct.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.feral.FeralService;

public final class FeralCatalystItem extends Item {
    private final boolean restoring;
    public FeralCatalystItem(boolean restoring) { super(new Settings()); this.restoring = restoring; }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient()) return TypedActionResult.success(stack);
        if (!(player instanceof ServerPlayerEntity sp) || !FeralService.start(sp, restoring)) {
            player.sendMessage(Text.translatable("ssc-primalstinct.feral." + (restoring ? "cannot_restore" : "cannot_apply")), true);
            return TypedActionResult.fail(stack);
        }
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        player.getItemCooldownManager().set(this, FeralService.DURATION);
        return TypedActionResult.consume(stack);
    }
}
