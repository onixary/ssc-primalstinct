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
        if (!net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) return false;
        Block block = player.getWorld().getBlockState(hit.getBlockPos()).getBlock();
        if (block instanceof BedBlock) return false;
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
                if (power.isActive() && power.prevents() && (power.getItemTag() == null
                        || stack.isIn(TagKey.of(RegistryKeys.ITEM, power.getItemTag())))) return true;
            }
        }
        return false;
    }

    /** Doors subject to a server-side roll must not open speculatively on the client. */
    public static boolean waitForDoorInteraction(PlayerEntity player, BlockHitResult hit) {
        if (!net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) return false;
        if (player.shouldCancelInteraction()
                && (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty())) return false;
        Block block = player.getWorld().getBlockState(hit.getBlockPos()).getBlock();
        if (!(block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock)) return false;
        return PowerHolderComponent.getPowers(player,
                net.onixary.sscPrimalstinct.power.factory.InteractionFailurePower.class)
                .stream().anyMatch(power -> power.isActive() && power.blockChance > 0);
    }

    /** Random decisions run only on the authoritative server, once per tick/position. */
    private static final java.util.Map<PlayerEntity, Roll> LAST_ROLL = new java.util.WeakHashMap<>();
    private record Roll(long tick, net.minecraft.util.math.BlockPos pos, boolean denied) {}
    public static boolean failsInteraction(net.minecraft.server.network.ServerPlayerEntity player, Hand hand, BlockHitResult hit) {
        if (blocksInteraction(player, hand, hit)) {
            player.sendMessage(net.minecraft.text.Text.translatable("message.ssc-primalstinct.interaction_certain"), true);
            return true;
        }
        if (!net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) return false;
        if (player.shouldCancelInteraction() && (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty())) return false;
        var state = player.getWorld().getBlockState(hit.getBlockPos());
        Block block = state.getBlock();
        if (block instanceof BedBlock) return false;
        boolean container = isContainerBlock(block) || player.getWorld().getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.inventory.Inventory;
        boolean interactive = container || isProcessingBlock(block) || state.createScreenHandlerFactory(player.getWorld(), hit.getBlockPos()) != null
                || block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock
                || block instanceof AbstractSignBlock || block instanceof JukeboxBlock || block instanceof NoteBlock
                || block instanceof RepeaterBlock || block instanceof ComparatorBlock || block instanceof BellBlock
                || block instanceof CakeBlock || block instanceof ComposterBlock || block instanceof RespawnAnchorBlock
                || block instanceof AbstractCauldronBlock || block instanceof CampfireBlock || block instanceof BeehiveBlock
                || state.isIn(TagKey.of(RegistryKeys.BLOCK, new net.minecraft.util.Identifier("ssc-primalstinct", "interactive_blocks")));
        if (!interactive) return false;
        float chance = PowerHolderComponent.getPowers(player, net.onixary.sscPrimalstinct.power.factory.InteractionFailurePower.class)
                .stream().filter(p -> p.isActive()).map(p -> container ? p.containerChance : p.blockChance).max(Float::compare).orElse(0f);
        if (chance <= 0) return false;
        long tick = player.getWorld().getTime();
        Roll last = LAST_ROLL.get(player);
        if (last != null && last.tick == tick && last.pos.equals(hit.getBlockPos())) return last.denied;
        boolean denied = player.getRandom().nextFloat() < chance;
        LAST_ROLL.put(player, new Roll(tick, hit.getBlockPos().toImmutable(), denied));
        // 白板"本能设计"：掷骰结果触发 success/failure 动作（仅新鲜掷骰，同 tick 同位复用不重复触发）
        for (net.onixary.sscPrimalstinct.power.factory.InteractionFailurePower power :
                PowerHolderComponent.getPowers(player, net.onixary.sscPrimalstinct.power.factory.InteractionFailurePower.class)) {
            if (!power.isActive()) continue;
            var action = denied ? power.failureAction : power.successAction;
            if (action != null) action.accept(player);
        }
        if (denied) player.sendMessage(net.minecraft.text.Text.translatable(chance >= 1
                ? "message.ssc-primalstinct.interaction_certain" : "message.ssc-primalstinct.interaction_random"), true);
        return denied;
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
        if (!net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) return false;
        if (!(handler instanceof PlayerScreenHandler) && !(handler instanceof CraftingScreenHandler)) return false;
        for (PreventInventoryCraftingPower power : PowerHolderComponent.getPowers(player, PreventInventoryCraftingPower.class)) {
            if (power.isActive() && (handler instanceof PlayerScreenHandler || power.includesCraftingTable())) return true;
        }
        return false;
    }
}
