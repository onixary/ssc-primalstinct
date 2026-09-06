# 卡15 替换本能 HUD 与预警表现 —— 实施记录

对应白板卡15（前置 14）。目标：保留原条位置与预警意图，换成新资源、分级刻度和锁定贴图。

## 落点一览

| 内容 | 文件 |
|---|---|
| HUD 渲染（条/速率分档/刻度/锁定/状态文本/dev 读数） | `client/ui/PrimalInstinctHud.java` |
| 限频预警粒子（新状态驱动） | `client/effect/PrimalstinctWarningParticles.java` |
| 客户端状态外推 + 平滑纠正 + 切世界清理 | `client/network/ClientPrimalstinctState.java` |
| 位置锚点/偏移配置 | `client/PrimalstinctClientConfig.java`（config/ssc-primalstinct-client.json） |
| SSC 客户端类集中引用 | `adapter/ssc/SSCClientAdapter.java`（法力顶替查询、变身粒子） |
| 旧条停用（MIXIN S6） | `mixin/ssc/client/InstinctBarRendererMixin.java` |
| 旧 clientTick 切断（MIXIN S1 追加） | `mixin/ssc/InstinctUtilsMixin.java` |
| 贴图 | `assets/ssc-primalstinct/textures/gui/primal_instinct_bar.png`（160×35，生成脚本 `tools/gen_primal_bar_texture.py`） |
| 协议增补 | `PrimalstinctStateS2C` 增加 `managed/maxValue/baseRate/thresholds` |

## 关键决策

1. **贴图与硬编码 y 值**：条行 v 常量集中在 `PrimalInstinctHud` 顶部并与生成脚本成对维护，不沿用旧 SSC 的 `y=35` 魔数。分级刻度不烘焙进贴图——阈值随快照同步（服务端等级表可被数据包自定义），改为按阈值位置程序化 `fill` 1px 刻线，适配任意阈值表。
2. **填充方向**：原版式左→右填充（左为 0、右为满值，用户指定）——填充取贴图行右半自 u=80 起的左端段，空槽取左半右侧剩余段；阈值刻线按 `左端 + 比例宽` 换算。增长行渐变为左暗右亮，条越接近满值新显露的填充越亮（警示递进）。
3. **速率分档外框（保留旧系统）**：贴图沿用旧 SSC 的"每行左半空槽 + 右半填充"结构（160 宽），整条外框与填充随分档变化。分档语义对齐旧 `updateBarTextures`：`rate` 相对 `baseRate` 的超出量分档——`>base` 微增、`>+0.005` 增长I（金）、`>+0.01` 增长II（橙）、`>+0.1` 增长III（红）；负向为下降档（青蓝）；基础自然增长（≈baseRate）保持平稳档（平静外观）。`baseRate` 随快照同步（服务端调整基础速率后客户端分档自动跟随）。
4. **权威与预测分离**：条长 = `value + rate × 经过时间` 外推并钳制（τ≈0.12s 平滑纠正）；跨阶提示与锁定覆盖只读快照 `level/locked`，多人高延迟下客户端不会据预测提前解除锁定表现。收到新快照按指数平滑纠正；断线（DISCONNECT→clear）与切世界（玩家实体更换→resetPrediction）清理上一世界预测值。
5. **跨阶提示**：走原版 actionbar 位置（`InGameHud.setOverlayMessage`，屏幕下方居中、与其它模组通用的标签表现，含原版淡出计时）。升阶 L1/L2/L3/L4 各自文案（`level_up.1–4`）、升至满值（最高级）显示锁定专用文案（`level_up.locked`）、降阶共用一条（`level_down`）；自定义等级表超出预设文案的等级回退通用 `level_up`。方向由连续快照的 level 对比得出（服务端每次跨阶即时 syncNow）——不使用平滑后速度差判向：平滑速度在速率趋 0 或纠正收敛阶段会误判，且服务端快照本就是权威；条形平滑保持纯视觉用途。
6. **显隐规则**：无玩家 / hudHidden / 旁观 / 创造（hasStatusBars=false）不绘制；无快照或 `managed=false`（pending、非名单形态）不绘制。名单内形态一律显示——不读 SSC 的 `NoInstinct` flag（flag 保留给 SSC 其他系统）。`managed` 由服务端按名单解析计算并随快照下发；`FORM_CHANGE_END` 触发即时 syncNow，不等待 40 tick 限频校正。
7. **法力共存**：新条不参与 SSC 的 `OverrideInstinctBar` 顶替链。法力条照常渲染在旧位置，新条在场时自动上移 14px（条 5 + 数字 9），有法力形态两类资源同屏；用户可用 offset 配置手工微调。位置默认值对齐 SSC 旧默认（posType=8, +100, −9），迁移体验一致。
8. **粒子频率**：单处 END_CLIENT_TICK 注册 + tick 冷却计数——高值区间 `[次高阈值, 满值)` 2/s、满值锁定后 0.5/s；与 FPS 无关，同 tick 重复回调也不会成倍生成。粒子类型沿用旧 SSC 的 `PLAYER_TRANSFORM_PARTICLE`。
9. **旧 HUD 禁用（接卡04）**：注入 `InstinctBarRenderer.render` HEAD-cancel（快照存在即取消），不注入 SSC 的 `InGameHudMixin`（其上还挂法力条/物品收纳渲染，整方法取消会误伤）。同时切断旧 `InstinctUtils.clientTick`（旧值冻结后残值会产生幽灵粒子/旧条虚影）。连到未安装本附属的 SSC 服务器时无快照，旧条照常工作。
10. **文本**：`hud.ssc-primalstinct.locked`「本能已满值锁定」与 `hud.ssc-primalstinct.paused`「本能增速暂停」明确分离，不沿用 SSC 临时变身锁文案；跨阶走 actionbar 分档文案（见 5）。条右侧状态文本可用配置关闭。
11. **dev 读数**：固定屏幕左上角（卡03 占位 HUD 原位，4,4），不随条位置移动、不出屏；显示外推值/等级/速率/锁定/revision，供边界验收核对。
12. **协议兼容**：`state_s2c` 追加字段属破坏性变更——本附属 0.1.0 未发布，客户端/服务端同 jar 同版本，无旧版本互操作需求（白板卡19 联机回归再整体确认）。

## 验收清单（行为验收，待游戏内执行）

- [ ] 边界显示：0 / 19.99 / 20 / 80 / 99.99 / 100 及下降过程条长、刻线对位正确（左上 dev 读数辅助核对）。
- [ ] 速率分档：基础自然增长平稳外观；叠加增速 Power 后外框/填充按 微增→I→II→III 逐档变化；负向下降青蓝档。
- [ ] 跨阶提示：升阶在屏幕下方居中出现且 L1/L2/L3/L4/锁定文案各不相同；降阶共用一条；原版淡出节奏。
- [ ] 满值：锁定覆盖 + 金色状态文本 + 0.5/s 粒子；`/primalstinct debug set value 100` 只进入新锁定。
- [ ] 多人高延迟：锁定/等级表现不因客户端外推提前解除（以服务端快照为准）。
- [ ] 有法力形态：法力条与新本能条同屏不重叠。
- [ ] 显隐：pending（未完成选择）、非名单形态、无玩家/旁观/创造/隐藏 HUD 时不绘制；名单内 NoInstinct 形态（ocelot_3 等）正常显示。
- [ ] 旧条不出现两根；断线重连/切世界后无残值虚影。
- [ ] 蜷缩睡眠（速率暂停）显示灰蓝「本能增速暂停」文本，与满值锁定文案可区分。
