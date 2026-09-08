package net.onixary.sscPrimalstinct.interaction;

import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

/**
 * 卡10：drop_tool_after_use 的共享掉落逻辑。
 * 掉落真实剩余堆栈（含耐久），带短拾取延迟；槽位身份不匹配（切槽）时宁可不掉不错丢。
 */
public final class ToolDropHelper {

    private ToolDropHelper() {
    }

    public static boolean isToolOrWeapon(Item item) {
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof PickaxeItem
                || item instanceof ShovelItem || item instanceof HoeItem || item instanceof TridentItem
                || item instanceof BowItem || item instanceof CrossbowItem || item instanceof ShearsItem;
    }

    /** 行为完成后掉落该手上的真实工具堆栈；空手/非工具/堆栈身份变化则不动作。 */
    public static void dropHeldTool(ServerPlayerEntity player, Hand hand) {
        dropHeldTool(player, hand, false);
    }

    public static void dropHeldTool(ServerPlayerEntity player, Hand hand, boolean attackEvent) {
        if (!net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty() || !isToolOrWeapon(stack.getItem())) {
            return;
        }
        float chance = io.github.apace100.apoli.component.PowerHolderComponent.getPowers(player,
                net.onixary.sscPrimalstinct.power.factory.DropToolAfterUsePower.class).stream()
                .filter(p -> p.isActive() && (!attackEvent || p.attack)).map(p -> p.chance).max(Float::compare).orElse(0f);
        if (chance <= 0 || player.getRandom().nextFloat() >= chance) return;
        player.sendMessage(net.minecraft.text.Text.translatable(chance >= 1
                ? "message.ssc-primalstinct.drop_certain" : "message.ssc-primalstinct.drop_random"), true);
        player.setStackInHand(hand, ItemStack.EMPTY);
        var drop = player.dropItem(stack, false, false);
        if (drop != null) {
            drop.setPickupDelay(30);
        }
    }

    public static void dropFiredWeapon(net.minecraft.entity.LivingEntity user, ItemStack stack) {
        if (!(user instanceof ServerPlayerEntity player)) return;
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand) == stack) { dropHeldTool(player, hand, true); return; }
        }
    }

    /** 供"行为开始记录、结束校验身份"的钩子使用：槽内堆栈仍是同一实例才掉。 */
    public static void dropIfSameInstance(ServerPlayerEntity player, Hand hand, @Nullable ItemStack recorded) {
        if (recorded == null || recorded.isEmpty()) {
            return;
        }
        ItemStack current = player.getStackInHand(hand);
        if (current == recorded) {
            dropHeldTool(player, hand);
        }
        // 切槽/消耗：宁可不掉不错丢
    }
}
