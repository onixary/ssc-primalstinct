package net.onixary.sscPrimalstinct.interaction;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.onixary.sscPrimalstinct.power.factory.PreventBlockPlacePower;
import net.onixary.sscPrimalstinct.power.factory.PreventContainersPower;
import net.onixary.sscPrimalstinct.power.factory.PreventDoorPower;
import net.onixary.sscPrimalstinct.power.factory.PreventInventoryCraftingPower;
import net.onixary.sscPrimalstinct.power.factory.PreventProcessingBlocksPower;

/** Shared by client prediction and server authority; evaluate current Power conditions. */
public final class InteractionRestrictions {
    private InteractionRestrictions() {}

    public static boolean blocksInteraction(PlayerEntity player, Hand hand, BlockHitResult hit) {
        Block block = player.getWorld().getBlockState(hit.getBlockPos()).getBlock();
        if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock) {
            for (PreventDoorPower power : PowerHolderComponent.getPowers(player, PreventDoorPower.class)) {
                if (power.isActive()) return true;
            }
        }
        if (isProcessingBlock(block)) {
            for (PreventProcessingBlocksPower power : PowerHolderComponent.getPowers(player, PreventProcessingBlocksPower.class)) {
                if (power.isActive()) return true;
            }
        }
        if (isContainerBlock(block)) {
            for (PreventContainersPower power : PowerHolderComponent.getPowers(player, PreventContainersPower.class)) {
                if (power.isActive()) return true;
            }
        }
        var stack = player.getStackInHand(hand);
        if (stack.getItem() instanceof BlockItem) {
            for (PreventBlockPlacePower power : PowerHolderComponent.getPowers(player, PreventBlockPlacePower.class)) {
                if (power.isActive() && (power.getItemTag() == null
                        || stack.isIn(TagKey.of(RegistryKeys.ITEM, power.getItemTag())))) return true;
            }
        }
        return false;
    }

    /** 原版物品加工方块：工作台/熔炉系/酿造台/切石机/织布机/制图台/砂轮/锻造台/铁砧。 */
    private static boolean isProcessingBlock(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof StonecutterBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof SmithingTableBlock
                || block instanceof AnvilBlock;
    }

    /** 容器方块：箱子/trapped箱/末影箱/木桶/潜影盒/漏斗/发射器/投掷器。 */
    private static boolean isContainerBlock(Block block) {
        return block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock;
    }

    public static boolean blocksCrafting(PlayerEntity player, ScreenHandler handler) {
        if (!(handler instanceof PlayerScreenHandler) && !(handler instanceof CraftingScreenHandler)) return false;
        for (PreventInventoryCraftingPower power : PowerHolderComponent.getPowers(player, PreventInventoryCraftingPower.class)) {
            if (power.isActive() && (handler instanceof PlayerScreenHandler || power.includesCraftingTable())) return true;
        }
        return false;
    }
}
