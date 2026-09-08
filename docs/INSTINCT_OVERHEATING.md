# InstinctOverheating / 本能过热

接管反馈：普通游荡和强制接管期间均显示黄色的原版 nausea 边缘叠加层，不附加反胃药水或镜头扭曲。退出接管即消失。创造模式完全不执行接管（包含过热效果）、不触发附近目标检测；切换为创造时释放接管。手动附加的过热标记本身仍可存在，但在创造模式不控制玩家。

效果 ID：`ssc-primalstinct:instinct_overheating`。纯标记 StatusEffect，不增加属性、不在效果 tick 中执行伤害或其他动作，等级不改变行为。图标暂复用 Minecraft 1.20.1 的抗火图标，名称为“本能过热”。

玩家持有**当前活跃**的 `instinct_wander_ai` Power 且处于托管形态时，效果会跳过 AFK 等待，强制运行代理 AI。客户端屏蔽手动方向、跳跃、潜行和疾跑；服务端拒绝按键恢复请求。鼠标仍可自由转动；攻击/使用键不解除接管。进入效果时退出睡眠/骑乘，效果期间不能以蜷缩或上床来逃离接管。

效果到期、被清除时主动释放代理，并从头计算正常 AFK 等待。没有 AI Power、Power 条件不满足、死亡、旁观、飞行或本能满值锁定时，效果不会绕过原有接管限制。Buff 可以继续保留，符合条件后再接管。效果为普通状态效果，支持原版 `/effect clear` 和移除效果机制。

## 目标条件

```json
{
  "type": "ssc-primalstinct:proxy_has_nearby_attack_target",
  "radius": 16.0
}
```

检测以玩家为中心的球形范围内，是否存在当前 AI 生物类型会主动选作攻击目标的实体。`radius` 默认 16，最大 128，非正数/非有限数返回 false。原生目标条件中的跟随距离、可攻击性、可见性、实体子条件仍生效，所以 radius 是额外上限，不会强行扩大原生感知范围。

每次条件评估创建短生命周期、未加入世界的代理，只读取其 `ActiveTargetGoal` 的目标类型和 TargetPredicate，并在 finally 中销毁。不会 tick 代理、移动玩家、执行攻击或改变正在接管的代理目标；手动控制期间也可检测。排除玩家自身、死亡实体和旁观者。

当前支持基于 `ActiveTargetGoal` 的目标规则（含标准子类，例如豹猫的鸡和陆地幼海龟筛选）。这表示**附近潜在猎物**，不是“正在攻击”的判定，不包含回避目标、仅受伤后报复、Brain 记忆或任意模组自行实现的完整 canStart 逻辑。检测不强制 AI 立刻攻击，实际行动仍由其原生 Goal 决定。

## 测试 Power

`ssc-primalstinct:ocelot_3_instinct_overheating_test` 已加入豹猫三阶的 base_powers。

| 配置 | 当前值 |
|---|---|
| `interval` | 600 tick，正常 TPS 下 30 秒 |
| `radius` | 16 格 |
| `chance` | 0.25，即 25% |
| 效果 `duration` | 200 tick，即 10 秒 |

全部采用本地 Apoli 2.9.2 已有的 `action_over_time`、`if_else`、`and`、`status_effect`、`chance`、`apply_effect`。**没有独立冷却**；成功或失败都等待下一个 30 秒周期。已有过热效果时跳过，不续时。把目标检测放在周期动作内，不会每 tick 扫描附近实体。

豹猫现有 AI Power 的条件为本能等级至少 3，因此测试自动触发也需要满足该条件。正常 AFK 等待沿用已有 Power 配置，没有在此测试 Power 中修改。

## 验证与手动测试

- `gradlew.bat test remapJar --offline`；新增强制/普通接管、在途释放、效果结束、断线清理及测试 JSON 约定的回归测试。
- 核对本地 Apoli/Calio 源码中的动作名称和效果字段；编译不能替代实机资源加载和 Mixin 注入验证。
- 本次没有启动游戏。

先切换豹猫三阶并满足 AI Power 的等级条件，用下列命令直接验证强制控制：

```mcfunction
/effect give @s ssc-primalstinct:instinct_overheating 10 0 true
/effect clear @s ssc-primalstinct:instinct_overheating
```

效果中持续按 WASD、空格、潜行、疾跑、攻击/使用，不应退出或改变移动；视角保持自由。结束后应立即可手动移动，重新等待正常 AFK 周期。随后在 16 格内放鸡测试周期触发；无猎物时不应触发。有猎物也有 75% 概率本轮不触发，可以临时把 JSON 的 chance 改成 1.0 来验证事件链，再恢复为 0.25。

客户端和服务端需要同时更新：`wander_motion` 数据包增加了 forced 标志。普通游荡以及此前修复的原版重力链路保持使用同一套移动逻辑。
