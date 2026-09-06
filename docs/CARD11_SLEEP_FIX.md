# 卡11：无床睡眠立即退出

## 已确认原因

MC 1.20.1 LivingEntity.tick 在 isSleeping=true 且 isSleepingInBed=false 时调用 wakeUp。原控制器设置地面 sleeping position，却未提供无床判定，因此在下一tick被原版踢醒。原版起床也未清理附属map，可能遗留本能速率并使下次按键只清旧状态。

原控制器没有调用 ServerWorld.updateSleepingPlayers，SleepManager缓存没有登记新睡眠者。allow_sleep 字段也没有使用。单设姿态/位置并不等于接入完整睡眠流程。

## 修改

- 新同步蜷缩标记，与实际sleeping position区分。仅此标记有效的玩家允许通过无床检查，正常床不变。
- 白天/allow_sleep=false/不支持床的维度只蜷缩休息，不打开睡眠界面、不计入跳时。夜间允许睡眠时清零sleepTimer、设置睡眠位置、更新世界统计；保留原版100tick及playersSleepingPercentage规则。
- 不调用床的位置偏移；本人及旁观玩家同步蜷缩标记，进入时先发标记再更新sleeping position。
- 主动起床、原版离开床按钮、黎明起床统一走原版wakeUp，清除附属状态和速率；增加断线、停服、失去能力、换世界、失去支撑清理。
- 脚下支撑使用碰撞检查，避免睡眠移动包导致onGround变化时误醒；实际受伤才退出。

## 待游戏内回归

1. 夜间平地、allow_sleep=true：按V睡眠，连续保持超过100tick；单人且睡眠比例满足时到第二天并正常醒来。重生点不被蜷缩修改。
2. 再按V、睡眠界面Leave Bed、实际受伤均正确起床；起床后sleep:rate被移除，下次按V可直接睡。
3. 白天、allow_sleep=false、下界/末地：只保持蜷缩，不闪睡眠界面、不跳时。
4. 满值本能不能通过POWER睡眠速率解锁；改变形态、能力失活、断线重登无残留。
5. 两人分别用床和蜷缩：睡眠人数比例及100tick规则正常；新进入视野的旁观者看到正确姿态与位置。
6. 完整方块、台阶边缘、脚下方块被挖掉分别验证支撑判断；普通床睡眠和拆床唤醒保持原行为。

已完成离线build及现有单元测试；未宣称已完成客户端/服务器内实测。
