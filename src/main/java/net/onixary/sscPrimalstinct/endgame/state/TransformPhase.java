package net.onixary.sscPrimalstinct.endgame.state;

/**
 * 眷属实现02/13：转化会话状态机。
 * IDLE→STARTED→FORM_APPLIED→COMPLETED 或 FAILED；
 * 完成以实际 FormID 生效为准（FORM_CHANGE_END / 登录对账），不以客户端“动画结束”包为准。
 */
public enum TransformPhase {
    IDLE,
    /** 资格判定通过、会话已持久化、SSC startTransform 已调用。 */
    STARTED,
    /** 目标 FormID 已实际生效（完成事务执行中）。 */
    FORM_APPLIED,
    /** 完成事务全部落定（能力结算、背包/阅读恢复同步、Info 已排队）。 */
    COMPLETED,
    /** 启动失败或被终止；玩家可重新尝试。 */
    FAILED
}
