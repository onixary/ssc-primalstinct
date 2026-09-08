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
| S5 | `InstinctValueCondition.condition(SerializableData.Instance, Entity)` | 卡04 切断→卡17 分流：接管态（受管理或 pending）恒 false（旧字段不作为新系统输入，附属能力用新等级条件）；普通路线透传 SSC 原判定 | 同上 | 引用该条件的 Power（蜘蛛满值粒子等）：接管玩家不再因旧值触发，未接管玩家照常 | **已实施**（InstinctValueConditionMixin，按玩家判定） | 接管形态的旧 instinct_value power 静默；普通路线（未开书/未进 Feral）旧 power 行为与无附属时一致 |
| S3 | 首登发书链路（`PlayerEventHandler` JOIN 触发 `ON_FIRST_JOIN_WITH_MOD.trigger` → 成就奖励发书，具体拦截点待定） | 卡17：停发幻形者之书，保留图鉴 | 同上 | 首次进入有 SSC 的世界的玩家获得书 | 计划（卡20 提示：需精确禁目标奖励并处理已 schedule 的发书残留） | 附属在场：新玩家首登无书；附属移除：发书恢复 |
| S4 | 幻形者之书合成配方注册/解析（目标待核，卡17 要求过滤所有有效索引） | 卡17：移除书的合成，保留图鉴内容 | 同上 | 书的合成配方可用 | 计划 | 附属在场：配方不可用；已有书仍可打开图鉴 |
| S6 | `InstinctBarRenderer.render(DrawContext, float)`（SSC 客户端，注入点为渲染入口本体而非 `InGameHudMixin`，保持 SSC 自有 mixin 不动；mana 的 `OverrideInstinctBar` 顶替链因此不受影响） | 卡15：停用旧 SSC 本能条避免两根并存；新 HUD 读新组件（`PrimalInstinctHud`）、独立于 mana 顶替链（法力条照常渲染在原位，新条自动上移 14px 避让），且不以 NoInstinct 判定隐藏（显隐走快照 managed，覆盖旧隐藏判定但不全局删 flag） | 类级 remap=false（SSC 自有类，方法名唯一） | 原本能条渲染；客户端接管态（快照 managed || selectionPending，卡17）时取消——未接管玩家与无附属服务器照常 | **已实施**（mixin/ssc/client/InstinctBarRendererMixin，client 数组，HEAD cancel，按玩家接管态判定） | 接管玩家：显示新 HUD（含 NoInstinct 形态）、旧条消失、法力形态两类资源同屏；未接管玩家与附属移除后：旧条照常 |
| S7 | ~~`BookOfShapeShifterScreenV2_P2.init()` 内 `CodexData.getDescText/getContentText` 调用（@Redirect）~~ | 卡16：管理形态的 INSTINCTS 列文本替换 + 列底头图。**已移除**（2026-09-07 迁移至 SSC 公开扩展点 `custom_ui/CodexInstinctColumnHooks`——SSC 1.10.0 本地重建，附属实现 `client/ui/PrimalstinctCodexColumnProvider`）：mixin 对 P2.init 三处精确调用点与布局算式的依赖属高脆弱注入（MC override 名 + 描述符 refmap 坑 + SSC UI 迭代易撞），官方接口下 SSC 重构对附属变为编译期可见 | — | — | **已移除**（迁移记录） | 行为等价验收沿用卡16清单：管理形态本能列新文本+头图、"+"详情同源、普通形态/优势劣势列原样 |

## mixin/vanilla（原版目标）

| # | 目标类/方法 | 替换目的 | 是否 remap | 受影响的原行为 | 状态 | 回归用例 |
|---|-------------|----------|-----------|----------------|------|----------|
| V1 | `Slot.canInsert/canTakeItems`、`ScreenHandler.internalOnSlotClick`、`PlayerInventory.insertStack`×2、`ServerPlayNetworkHandler.onUpdateSelectedSlot` | 卡09：渐进槽位锁定的虚拟占位方案（客户端占位绘制+服务端锁槽规则，不放真实占位物品）。Slot 层闸门挡住所有经 ScreenHandler 的流入/取出；点击级防御覆盖数字键交换/丢弃/创造中键，QUICK_CRAFT 在 stage 1 把锁定槽挡在拖拽集外；insertStack 定向插入覆盖捡物//give/正规 mod 塞物；锁定快捷栏槽拒绝被选中并回发校正 | 标准 refmap | 原版无限制 | **已实施**（4 个 Mixin，mixin/vanilla） | 守恒：升级/降级/死亡/重生往返"库存+暂存+世界掉落"无损（已实测）；give 在受限时正确落地不掉入锁定槽；GUI 交互（点击/拖拽/滚轮/数字键）用户 playtest |
| V2 | （卡10 落地时补充）装备穿戴/交互入口 | 卡10：装备掉落与交互限制 | 标准 refmap | 原版无限制 | 计划 | 受限形态无法穿戴装备；成功使用工具后掉落 |
| V3 | （卡11 落地时补充）睡眠/蜷缩相关入口 | 卡11：蜷缩与睡眠玩法 | 标准 refmap | 原版睡眠规则 | 计划 | 蜷缩姿态进入/退出；睡眠恢复可用 |
| V4 | `ServerPlayerEntity.updateInput(FFZZ)`（HEAD 只读记录） | 游荡 AI 的骑乘输入补充信号。MC 1.20.1 普通步行不发送 PlayerInputC2SPacket；步行/跳跃/潜行/攻击/使用输入由客户端经 C2S 上报，DirectInputTracker 保留 2 tick 容差。鼠标转向不参与 AFK 或退出检测 | 标准 refmap | 无行为改变（只读取） | **已实施**（ServerPlayerEntityInputMixin，mixin/vanilla） | 游荡中按键释放接管；顶墙按键重置 idle 计时 |
| V5 | `MobEntity.goalSelector`（Accessor） | 仅创建未入世界的游荡代理时移除 TemptGoal，避免代理被重合的持食物玩家吸引而持续停止寻路；普通世界生物不变 | 标准 refmap | 无自动注入行为 | **已实施**（MobEntityGoalSelectorAccessor，mixin/vanilla） | 主/副手持生鱼时仍可游荡 |

## 复核记录

- 本能过热：新增 `ActiveTargetGoalAccessor` 读取 targetClass/targetPredicate，并扩展 `MobEntityGoalSelectorAccessor` 读取 targetSelector，仅供短生命周期探测代理使用。新增客户端 `KeyboardInputOverheatingMixin` 在 tick TAIL 清空手动移动输入，只有服务端下发 forced 接管期间启用，其他玩家输入不变。标准 refmap；测试覆盖强制/普通控制转换，实机验证见 `INSTINCT_OVERHEATING.md`。

- 坠落卡顿修复：`LivingEntityWanderJumpMixin` 增加 `LivingEntity.jump` TAIL 只读捕获（仅已标记代理），供一次性起跳指令使用；`getJumpVelocity` 高度倍率保持。旧服务端 move + 完整速度同步已改为 `WanderMotionS2C` 水平意图/起跳指令，客户端保留原版重力与位置上报。26 项测试和打包通过，实机待用户验证。

- 游荡跳跃高度：新增 `LivingEntityWanderJumpMixin`，在 `LivingEntity.getJumpVelocity` RETURN 调整返回值，标准 refmap。每实体倍率默认 1，仅游荡代理创建时设置 `jump_height_multiplier`。普通玩家和世界生物不改变。按原版陆地重力与阻力换算高度倍率，新增数值回归测试；实机跳跃仍受碰撞和玩家同步影响。

- 2026-09-06：建立清单（框架阶段，无已实施 Mixin；两条 mixin 配置均为空数组，先验证配置链路本身）。
- 2026-09-06：卡01 框架验收通过——附属 0.1.0 与 SSC 1.10.0（本地 remap 产物）在开发客户端与 dedicated server 均启动成功（服务器 58 个模组、双方初始化日志正常，无 Mixin 注入失败）。
- 2026-09-06（卡04）：旧本能写入路径调用点核查——`addInstinctEffect` 调用方仅 `AddImmediateInstinctPower`/`AddInstinctAction`/`AddSustainedInstinctPower`；`clearInstinct` 仅被 `checkThreshold`（随 serverTick 一并失效）与命令路径引用；`instinct_value` 条件仅被 power 数据引用（蜘蛛系粒子）。SSC `ItemStackMixin` 不直接调用这些方法（只处理变形效果清除/牛奶），故无需注入——金苹果原版效果保留，其旧抑制路径经 S2 废弃自然失效。旧 `playerInstinctLock`（诅咒之月/变身动画临时锁）仅被旧 serverTick 读取，对新资源无影响；旧变身动画继续可用其视觉锁。
- 2026-09-07（卡15）：S6 落地——注入目标选 `InstinctBarRenderer.render`（SSC 客户端类、单方法）而非 SSC 的 `mixin/InGameHudMixin`：后者还挂法力条与 ItemStorePowerRender 渲染，整方法取消会误伤。旧 `clientTick` 调用点核查：仅 `ShapeShifterCurseFabricClient` END_CLIENT_TICK 一处；切断后 `nowInstinctTick` 停增，`getNowInstinct` 冻结在旧值（旧条已被 S6 取消，无消费者）。新预警粒子改为单处注册 + tick 冷却（高值 2/s、锁定 0.5/s），与 FPS 及重复回调解耦。
- 2026-09-07（卡16）：S7 落地——注入选 P2 `init()` 的 CodexData 调用重定向而非复制整页布局：`BuildDetailScreenButton` 等 SSC 辅助方法为 private 无法继承覆写，而 @Redirect 一处覆盖主列/详情两读点且天然同源；P1→P2 翻页与附属快捷键直达构造同一 P2 类，单 mixin 覆盖全部入口。SSC 无 V1/V2 调色菜单选择配置，快捷键固定 V2（与 SSC 自身 P1/包路径一致）。
- 2026-09-08（游荡 AI 修复，含第二轮）：AFK 速度只检测水平分量，速度同步使用包含玩家自身的 `velocityModified`。代理保留重力、每 tick 递增 age 并重置 despawnCounter，补齐未入世界实体缺失的生命周期更新，避免 WanderAroundGoal 在计数达到 100 后永久拒绝启动。V5 仅移除代理的食物吸引 Goal。完全取消镜头同步与鼠标活动检测；按键经 C2S 释放接管。非托管态清理代理，跨世界重建，离线清除输入时间戳。干净编译、现有单元测试及 remapJar 通过，实机行为待用户验证。详见 WANDER_AI_FIX.md。
- 2026-09-07（卡16 修订）：S7 整体迁移至 SSC 公开扩展点 `CodexInstinctColumnHooks`（SSC 1.10.0 原版本地重建，含 P2 页面侧取数与列底贴图绘制；附属侧为 `PrimalstinctCodexColumnProvider`）。评估结论：该注入点钉在三处精确调用点 + 复刻布局算式 + MC override refmap 坑，属高脆弱注入且 P2 为活跃迭代 UI；官方接口把 SSC 重构风险从运行期崩溃转为编译期可见。其余 SSC mixin（HEAD cancel 类 + getPlayerList 过滤）风险低，维持现状。
