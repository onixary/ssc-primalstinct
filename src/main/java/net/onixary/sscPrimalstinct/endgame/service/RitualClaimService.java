package net.onixary.sscPrimalstinct.endgame.service;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlock;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlockEntity;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import net.onixary.sscPrimalstinct.endgame.state.EndgameWorldState;
import net.onixary.sscPrimalstinct.items.RegPrimalstinctItems;

/**
 * 眷属实现06（领取事务，A 阶段最小版）：
 * 预检 ACTIVE/资格/实例未领 → 标记实例 SPENT → 替换失效方块 → 产出 1 个镇静碎片
 * → 仅向成功领取者发 Info。
 * 一次性守卫：EndgameWorldState.markRewardClaimed 在同 tick 的双人/双手重复请求中只成功一次。
 * 奖励输出为祭坛上方短暂归属领取者的 ItemEntity，不强塞锁定背包。
 */
public final class RitualClaimService {

    private RitualClaimService() {
    }

    public static boolean tryClaim(ServerPlayerEntity player, World world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PrimalAltarBlock) || !(world instanceof ServerWorld serverWorld)) {
            return false;
        }
        // 1) 方块状态：必须 ACTIVE（三路供能有效）
        if (!state.get(PrimalAltarBlock.ACTIVE)) {
            feedback(player, "ssc-primalstinct.endgame.altar.not_active");
            return false;
        }
        BlockEntity raw = world.getBlockEntity(pos);
        if (!(raw instanceof PrimalAltarBlockEntity altar) || altar.getRitualId() == null) {
            feedback(player, "ssc-primalstinct.endgame.altar.no_instance");
            return false;
        }
        // 2) 玩家资格：名单内形态 + 实际最高级（唯一规则入口 EndgameEligibility）
        if (!EndgameEligibility.canClaimReward(player)) {
            feedback(player, "ssc-primalstinct.endgame.altar.not_eligible");
            return false;
        }
        // 3) 一次性守卫：世界状态标记（重复请求在此被拒绝）
        boolean first = EndgameWorldState.get(serverWorld).markRewardClaimed(world, pos, altar.getRitualId());
        if (!first) {
            SSCPrimalstinct.LOGGER.debug("[primalstinct] 祭坛 {} 奖励重复请求被拒绝（玩家 {}）",
                    pos, player.getGameProfile().getName());
            return false;
        }
        // 4) 替换为失效祭坛（独立方块；SPENT 不回退）；替换前先熄灭关联导线（眷属实现06）
        altar.extinguishWires();
        world.setBlockState(pos, RegEndgameBlocks.SPENT_PRIMAL_ALTAR.getDefaultState());
        // 5) 产出镇静碎片（归属领取者的短暂 ItemEntity）
        ItemStack reward = new ItemStack(RegPrimalstinctItems.SEDATIVE_FRAGMENT, EndgameRules.SEDATIVE_REWARD_COUNT);
        ItemEntity drop = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, reward);
        drop.setToDefaultPickupDelay();
        serverWorld.spawnEntity(drop);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        // 6) 仅向领取者弹分支说明（正式对话框 Info 见眷属实现14）
        player.sendMessage(Text.translatable("ssc-primalstinct.endgame.altar.claimed"), false);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 领取祭坛 {} 的镇静碎片（ritual={}）",
                player.getGameProfile().getName(), pos, altar.getRitualId());
        return true;
    }

    private static void feedback(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), true);
    }
}
