# SSC-Primalstinct Mixin 注入清单

对应白板卡 01 的要求：每个 Mixin 注入点必须在此登记（目标类/方法、替换目的、是否 remap、受影响的原行为、回归用例），先登记后实施。SSC 内部类目标与 Minecraft 原版方法分开处理：

- **mixin/ssc**（配置 `ssc-primalstinct-ssc.mixins.json`）：注入 SSC 内部类。SSC 自身的类名/成员名在发布产物中不变；方法签名中引用的 Minecraft 类型会经 Loom refmap 重映射，不需要对 SSC 成员单独设置 `remap = false`，除非注入目标本身是 Minecraft 类的 override。
- **mixin/vanilla**（配置 `ssc-primalstinct.mixins.json`）：注入原版类，全部走标准 refmap 重映射。

基线：SSC 提交 `14480ce7`（项目版本 1.10.0）。升级 SSC 基线时必须逐行复核本清单。

## 状态说明

- **计划**：来自白板策划，尚未核实到方法签名级
- **已核实**：已在 SSC 1.10.0 源码中确认目标签名
- **已实施**：Mixin 已存在且有回归用例

## mixin/ssc（SSC 内部目标）

| # | 目标类/方法 | 替换目的 | 是否 remap | 受影响的原行为 | 状态 | 回归用例 |
|---|-------------|----------|-----------|----------------|------|----------|
| S1 | `InstinctUtils.serverTick(MinecraftServer)`、`InstinctUtils.clientTick()` | 卡04：切断旧本能增长/满值变身/清零整链（不只拦 checkThreshold，其外仍有写值、冻结与同步）。卡15 追加 clientTick：废弃旧值外推计数与高值粒子路径（旧值已冻结，存量残值会导致幽灵粒子/旧条虚影） | MC 类型经 refmap | 原每 tick 增长、诅咒之月/锁冻结、满值 `checkThreshold→TransformManager.startTransform→clearInstinct`；客户端旧 `nowInstinctTick` 递增与 [80,99.99) 每tick粒子 | **已实施**（InstinctUtilsMixin，HEAD cancel；clientTick 在 client 数组外的公共 mixins 中，双端注入均安全——服务端从不调用该方法） | 附属在场：`/primalstinct debug set value 100` 只触发新锁定，不进化不清零；旧 instinctValue 冻结不变；附属移除：旧增长恢复 |
| S2 | `InstinctUtils.clearInstinct(PlayerEntity)` / `addInstinctEffect` ×2 重载 | 卡04：废弃旧效果写入（金苹果抑制、AddInstinctPower 系列全部失效） | 同上 | 旧值清零与效果增减 | **已实施**（同上 Mixin） | 吃金苹果/牛奶/触发旧 AddInstinct Power 后新值不变；原版金苹果吸收等效果保留 |
| S5 | `InstinctValueCondition.condition(SerializableData.Instance, Entity)` | 卡04：旧 instinct_value 条件恒 false（旧字段不作为新系统输入） | 同上 | 引用该条件的 Power（蜘蛛满值粒子等）按旧值比较 | **已实施**（InstinctValueConditionMixin） | 含 instinct_value 条件的 power 不再因旧值变化触发；卡06/07 以新等级条件重建 |
| S3 | 首登发书链路（`PlayerEventHandler` JOIN 触发 `ON_FIRST_JOIN_WITH_MOD.trigger` → 成就奖励发书，具体拦截点待定） | 卡17：停发幻形者之书，保留图鉴 | 同上 | 首次进入有 SSC 的世界的玩家获得书 | 计划（卡20 提示：需精确禁目标奖励并处理已 schedule 的发书残留） | 附属在场：新玩家首登无书；附属移除：发书恢复 |
| S4 | 幻形者之书合成配方注册/解析（目标待核，卡17 要求过滤所有有效索引） | 卡17：移除书的合成，保留图鉴内容 | 同上 | 书的合成配方可用 | 计划 | 附属在场：配方不可用；已有书仍可打开图鉴 |
| S6 | `InstinctBarRenderer.render(DrawContext, float)`（SSC 客户端，注入点为渲染入口本体而非 `InGameHudMixin`，保持 SSC 自有 mixin 不动；mana 的 `OverrideInstinctBar` 顶替链因此不受影响） | 卡15：停用旧 SSC 本能条避免两根并存；新 HUD 读新组件（`PrimalInstinctHud`）、独立于 mana 顶替链（法力条照常渲染在原位，新条自动上移 14px 避让），且不以 NoInstinct 判定隐藏（显隐走快照 managed，覆盖旧隐藏判定但不全局删 flag） | 类级 remap=false（SSC 自有类，方法名唯一） | 原本能条渲染；快照存在（=服务端运行本附属）时取消，连到无附属 SSC 服务器时旧条照常 | **已实施**（mixin/ssc/client/InstinctBarRendererMixin，client 数组，HEAD cancel） | 附属在场：显示新 HUD（含 ocelot_3 等 NoInstinct 形态）、旧条消失；有法力形态两类资源同屏；附属移除：原 HUD 恢复 |

## mixin/vanilla（原版目标）

| # | 目标类/方法 | 替换目的 | 是否 remap | 受影响的原行为 | 状态 | 回归用例 |
|---|-------------|----------|-----------|----------------|------|----------|
| V1 | `Slot.canInsert/canTakeItems`、`ScreenHandler.internalOnSlotClick`、`PlayerInventory.insertStack`×2、`ServerPlayNetworkHandler.onUpdateSelectedSlot` | 卡09：渐进槽位锁定的虚拟占位方案（客户端占位绘制+服务端锁槽规则，不放真实占位物品）。Slot 层闸门挡住所有经 ScreenHandler 的流入/取出；点击级防御覆盖数字键交换/丢弃/创造中键，QUICK_CRAFT 在 stage 1 把锁定槽挡在拖拽集外；insertStack 定向插入覆盖捡物//give/正规 mod 塞物；锁定快捷栏槽拒绝被选中并回发校正 | 标准 refmap | 原版无限制 | **已实施**（4 个 Mixin，mixin/vanilla） | 守恒：升级/降级/死亡/重生往返"库存+暂存+世界掉落"无损（已实测）；give 在受限时正确落地不掉入锁定槽；GUI 交互（点击/拖拽/滚轮/数字键）用户 playtest |
| V2 | （卡10 落地时补充）装备穿戴/交互入口 | 卡10：装备掉落与交互限制 | 标准 refmap | 原版无限制 | 计划 | 受限形态无法穿戴装备；成功使用工具后掉落 |
| V3 | （卡11 落地时补充）睡眠/蜷缩相关入口 | 卡11：蜷缩与睡眠玩法 | 标准 refmap | 原版睡眠规则 | 计划 | 蜷缩姿态进入/退出；睡眠恢复可用 |

## 复核记录

- 2026-09-06：建立清单（框架阶段，无已实施 Mixin；两条 mixin 配置均为空数组，先验证配置链路本身）。
- 2026-09-06：卡01 框架验收通过——附属 0.1.0 与 SSC 1.10.0（本地 remap 产物）在开发客户端与 dedicated server 均启动成功（服务器 58 个模组、双方初始化日志正常，无 Mixin 注入失败）。
- 2026-09-06（卡04）：旧本能写入路径调用点核查——`addInstinctEffect` 调用方仅 `AddImmediateInstinctPower`/`AddInstinctAction`/`AddSustainedInstinctPower`；`clearInstinct` 仅被 `checkThreshold`（随 serverTick 一并失效）与命令路径引用；`instinct_value` 条件仅被 power 数据引用（蜘蛛系粒子）。SSC `ItemStackMixin` 不直接调用这些方法（只处理变形效果清除/牛奶），故无需注入——金苹果原版效果保留，其旧抑制路径经 S2 废弃自然失效。旧 `playerInstinctLock`（诅咒之月/变身动画临时锁）仅被旧 serverTick 读取，对新资源无影响；旧变身动画继续可用其视觉锁。
- 2026-09-07（卡15）：S6 落地——注入目标选 `InstinctBarRenderer.render`（SSC 客户端类、单方法）而非 SSC 的 `mixin/InGameHudMixin`：后者还挂法力条与 ItemStorePowerRender 渲染，整方法取消会误伤。旧 `clientTick` 调用点核查：仅 `ShapeShifterCurseFabricClient` END_CLIENT_TICK 一处；切断后 `nowInstinctTick` 停增，`getNowInstinct` 冻结在旧值（旧条已被 S6 取消，无消费者）。新预警粒子改为单处注册 + tick 冷却（高值 2/s、锁定 0.5/s），与 FPS 及重复回调解耦。
