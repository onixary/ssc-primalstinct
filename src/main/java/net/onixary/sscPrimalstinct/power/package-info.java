/**
 * 卡06/07：形态与等级 Power 的统一挂载结算，以及本能侧 Power/Action/条件注册。
 * 约束：结算在服务端主线程进行，先做注册就绪检查（不采用 FormUtils.applyPower 式的异步重试）。
 */
package net.onixary.sscPrimalstinct.power;
