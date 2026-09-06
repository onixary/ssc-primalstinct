package net.onixary.sscPrimalstinct.instinct;

/**
 * 卡05：本能变化来源分级。
 * POWER：一切普通 Apoli entity action 改值的默认归属——JSON 不能自声明来源绕锁；
 * ITEM_RECOVERY / WORLD_RECOVERY：仅经服务端验证的物品/世界交互入口调用（卡14）；
 * ADMIN：调试命令等管理入口。
 */
public enum PrimalstinctSource {
    POWER,
    ITEM_RECOVERY,
    WORLD_RECOVERY,
    ADMIN
}
