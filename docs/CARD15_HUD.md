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
| 贴图 | `assets/ssc-primalstinct/textures/gui/instinct_bar.png`（自 SSC 复制；档位分界线已由美术画入贴图右下角未使用区 u=80..160, v=35..40） |
| 协议增补 | `PrimalstinctStateS2C` 增加 `managed/maxValue/baseRate/thresholds` |

## 关键决策

1. **条与外框完全复刻 SSC**（用户验收前反馈）：`renderInstinctBar`/`updateBarTextures` 逐行复刻——160×40 八行贴图（行 10 未使用）、`rate` 相对 `baseRate` 裸比较选行（>base 微增 15、>+0.005 行20、>+0.01 行25、>+0.1 行30、<0 行0、其余行5）、右锚两段取图（填充自右向左）、满值锁定行 y=35。贴图复制到本命名空间由美术修改区分。与 SSC 的差异仅在数据来源：条长用新系统外推值（平滑纠正）、锁定读快照 locked（SSC 旧判定来源已随卡04 失效）、`baseRate` 随快照同步。
2. **填充方向**：SSC 原版右锚（值增长自右向左，填充锚定条右端）——"完全使用原版SSC逻辑"已覆盖此前"左→右"的临时调整；如需改回左→右仅改 renderBar 两段取图。
3. **档位分界叠加线**：保留，由美术直接画在 instinct_bar.png 未使用的右下角（u=80..160, v=35..40，即锁定行右半；默认刻线位置 20/40/60/80 对应条内 x=16/32/48/64），渲染时从同一贴图该区域取图叠加在最上层；**锁定时不渲染**（锁定行整体覆盖，无需分界提示）。刻线位置不随服务端阈值表自适应——修改等级表时需同步改图。
4. **权威与预测分离**：条长 = `value + rate × 经过时间` 外推并钳制（τ≈0.12s 平滑纠正）；跨阶提示与锁定覆盖只读快照 `level/locked`，多人高延迟下客户端不会据预测提前解除锁定表现。收到新快照按指数平滑纠正；断线（DISCONNECT→clear）与切世界（玩家实体更换→resetPrediction）清理上一世界预测值。
5. **跨阶提示**：走原版 actionbar 位置（`InGameHud.setOverlayMessage`，屏幕下方居中、与其它模组通用的标签表现，含原版淡出计时）。升阶 L1/L2/L3/L4 各自文案（`level_up.1–4`）、升至满值（最高级）显示锁定专用文案（`level_up.locked`）、降阶共用一条（`level_down`）；自定义等级表超出预设文案的等级回退通用 `level_up`。方向由连续快照的 level 对比得出（服务端每次跨阶即时 syncNow）——不使用平滑后速度差判向：平滑速度在速率趋 0 或纠正收敛阶段会误判，且服务端快照本就是权威；条形平滑保持纯视觉用途。
6. **显隐规则**：无玩家 / hudHidden / 旁观 / 创造（hasStatusBars=false）不绘制；无快照或 `managed=false`（pending、非名单形态）不绘制。名单内形态一律显示——不读 SSC 的 `NoInstinct` flag（flag 保留给 SSC 其他系统）。`managed` 由服务端按名单解析计算并随快照下发；`FORM_CHANGE_END` 触发即时 syncNow，不等待 40 tick 限频校正。
7. **法力共存**：新条不参与 SSC 的 `OverrideInstinctBar` 顶替链。法力条照常渲染在旧位置，新条在场时自动上移 14px（条 5 + 数字 9），有法力形态两类资源同屏；用户可用 offset 配置手工微调。位置默认值对齐 SSC 旧默认（posType=8, +100, −9），迁移体验一致。
8. **粒子频率**：单处 END_CLIENT_TICK 注册 + tick 冷却计数——高值区间 `[次高阈值, 满值)` 2/s、满值锁定后 0.5/s；与 FPS 无关，同 tick 重复回调也不会成倍生成。粒子类型沿用旧 SSC 的 `PLAYER_TRANSFORM_PARTICLE`。
9. **旧 HUD 禁用（接卡04）**：注入 `InstinctBarRenderer.render` HEAD-cancel（快照存在即取消），不注入 SSC 的 `InGameHudMixin`（其上还挂法力条/物品收纳渲染，整方法取消会误伤）。同时切断旧 `InstinctUtils.clientTick`（旧值冻结后残值会产生幽灵粒子/旧条虚影）。连到未安装本附属的 SSC 服务器时无快照，旧条照常工作。
10. **文本**：条右侧不渲染任何状态文本（用户指定）——锁定由锁定行贴图、升满 actionbar 文案表达；速率暂停状态仅通过条外框/填充的平稳档表现。跨阶走 actionbar 分档文案（见 5）。
11. **dev 读数**：固定屏幕左上角（卡03 占位 HUD 原位，4,4），不随条位置移动、不出屏；显示外推值/等级/速率/锁定/revision，供边界验收核对。
12. **协议兼容**：`state_s2c` 追加字段属破坏性变更——本附属 0.1.0 未发布，客户端/服务端同 jar 同版本，无旧版本互操作需求（白板卡19 联机回归再整体确认）。

## 验收清单（行为验收，待游戏内执行）

- [ ] 边界显示：0 / 19.99 / 20 / 80 / 99.99 / 100 及下降过程条长、刻线对位正确（左上 dev 读数辅助核对）。
- [ ] 速率分档：基础自然增长平稳外观；叠加增速 Power 后外框/填充按 微增→I→II→III 逐档变化；负向下降青蓝档。
- [ ] 跨阶提示：升阶在屏幕下方居中出现且 L1/L2/L3/L4/锁定文案各不相同；降阶共用一条；原版淡出节奏。
- [ ] 满值：锁定行覆盖 + 升至满值的 actionbar 文案 + 0.5/s 粒子，条侧无附加文本；`/primalstinct debug set value 100` 只进入新锁定。
- [ ] 多人高延迟：锁定/等级表现不因客户端外推提前解除（以服务端快照为准）。
- [ ] 有法力形态：法力条与新本能条同屏不重叠。
- [ ] 显隐：pending（未完成选择）、非名单形态、无玩家/旁观/创造/隐藏 HUD 时不绘制；名单内 NoInstinct 形态（ocelot_3 等）正常显示。
- [ ] 旧条不出现两根；断线重连/切世界后无残值虚影。
- [ ] 蜷缩睡眠（速率暂停）时条侧无文本，条保持平稳档外观；满值锁定同样无条侧文本。
