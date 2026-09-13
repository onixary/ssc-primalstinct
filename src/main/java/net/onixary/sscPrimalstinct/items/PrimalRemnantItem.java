package net.onixary.sscPrimalstinct.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.service.RemnantService;

/**
 * 眷属实现07：原初残余（指引道具）。
 * 生存常规获取途径是最高级击杀自动触发的飞行表现（不进背包）；
 * 本物品仅供创造模式取得与手动使用，手动指引走与自动触发同一个 launch 服务。
 * 定位成功才消耗（创造不消耗）；无结构/范围耗尽等失败不消耗（服务端已限频提示）。
 */
public class PrimalRemnantItem extends Item {

    public PrimalRemnantItem(Settings settings) {
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
        RemnantService.LaunchResult result = RemnantService.tryLaunchFromItem(player);
        switch (result) {
            case LAUNCHED:
                if (!player.isCreative()) {
                    stack.decrement(1);
                }
                SSCPrimalstinct.LOGGER.debug("[primalstinct] 玩家 {} 手动使用原初残余，指引已投放",
                        player.getGameProfile().getName());
                return TypedActionResult.consume(stack);
            case CAPPED:
                // 在途数量已满：静默失败（残余已在飞行，不重复提示）
                return TypedActionResult.fail(stack);
            case NO_TARGET:
            case RANGE_EXHAUSTED:
            default:
                // 定位失败：物品不消耗（失败提示由服务端限频发送）
                return TypedActionResult.fail(stack);
        }
    }
}
