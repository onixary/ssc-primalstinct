# 野性化表现

`ssc-primalstinct:feral_crystal`（野性结晶）将白名单内、非四足的最终形态切换为 Feral 表现。默认白名单是 `shape-shifter-curse:axolotl_3` 和 `shape-shifter-curse:bat_3`。药水临时变形、诅咒之月实际造成的临时形态、已野性化或正在变形时不消耗结晶。

`ssc-primalstinct:restoration_dust`（复原粉尘）撤销野性化。两者右键使用，成功消耗 1 个（创造模式不消耗），失败保留物品并显示本地化提示。

两条无序祭坛配方制作时间均为 200 tick（10 秒），额外燃料均为 4 月尘（3200 燃料单位）：

| 产物 | 材料 | 数量 |
| --- | --- | --- |
| 野性结晶 | 回响碎片 + 腐肉 + 月尘 `shape-shifter-curse:untreated_moondust` | 1 |
| 复原粉尘 | 金粒 + 紫水晶碎片 + 月尘 | 1 |

## 配置

数据包路径：`data/<namespace>/feral_presentations/<name>.json`。覆盖内置文件或增加文件即可修改名单，`/reload` 生效。字段中的形态 ID 必须已注册。一个形态只能有一条启用配置；无效配置会记录错误并保留之前的名单。

```json
{
  "form": "shape-shifter-curse:axolotl_3",
  "enabled": true,
  "replace_model": false,
  "replace_animation": false,
  "scale": 1.0,
  "eye_scale": 0.6,
  "remove_powers": ["shape-shifter-curse:form_axolotl_3_crawling"]
}
```

- `scale`：身体宽度与高度缩放，默认 `1.0`。
- `eye_scale`：眼高与碰撞箱高度缩放，默认 `0.6`。两项均须为有限正数，语义与 SSC `applyScale(scale, eyeScale)` 一致；同时更新持物渲染使用的默认眼高基准。
- `remove_powers`：野性化期间移除的原形态 Power ID 数组，默认空数组。只移除真实形态 Origin source 的授权，保留其他来源的同名能力；复原或配置取消屏蔽时补回实际移除的授权与保存的 Power 状态。切到不同形态时丢弃旧记录，由 SSC 加载新形态能力，避免旧能力泄漏。屏蔽记录随玩家保存，重载能力后重新移除。
- `enabled`：默认 true；false 禁用该条配置。
- `replace_model`：默认 false，保留自身模型和贴图。true 时提供 `model_form` 借用已注册形态，或提供 `model_origin` 直接引用 `ssc_form_model` 配置的 `form` 字段（固定 `origins:origin` 层）；两者不能同时指定。相应客户端资源须可用。
- `replace_animation`：默认 false，使用 SSC 通用四足动画。true 时必须提供 `animation_form`，使用该形态的状态动画控制器。

例如保留蝙蝠模型但使用豹猫的运动动画：

```json
{
  "form": "shape-shifter-curse:bat_3",
  "replace_model": false,
  "replace_animation": true,
  "animation_form": "shape-shifter-curse:ocelot_3"
}
```

服务端将当前配置的表现选择与玩家状态一起同步给本人及跟踪该玩家的客户端，不要求客户端另装同一数据包。

## 行为边界

- 使用 SSC 原有的双足→四足、四足→双足变形动画和屏幕效果。演出时长沿用 SSC：3 秒入段、5 秒出段，在黑屏处切换表现。
- 原始 `PlayerFormComponent.nowForm`、FormID、Origin、技能树、形态历史均不修改；除 `remove_powers` 明确指定的能力外，其他 Power 实例保持不变；缩放使用野性化配置。技能专用动画仍由真实形态处理。
- `FormTextureUtils.getPlayerForm_Render` 返回带 FERAL 体型的玩家独立表现视图，供嘴部持物、手持物隐藏、披风、姿态和动画等 SSC 表现逻辑使用；不全局修改注册形态，也不改变怪物 AI 等真实形态判定。
- 野性化中点应用配置缩放，复原或解除时应用真实形态的缩放；重登/重生、配置热重载、同形态重载与能力重建也会恢复正确缩放。Pehkui 负责物理缩放同步，表现组件同步默认眼高基准。
- 同形态重载保留野性化。真实形态切换自动清除；恢复不会调用主模组的形态切换或能力重建。
- 白名单只匹配真实主形态 ID，不向子形态继承。`ISubForm.isSubForm()` 为 true 时，即使配置显式列出该 ID 也不会生效。切换子形态清除野性化，切回主形态不会恢复旧状态；存档或同步中的残留子形态状态不会用于渲染，并由服务端清除。
- 游泳姿态与碰撞判定按用户要求沿用 SSC 的 Feral 行为；不隔离 `PlayerEntityPoseMixin`。这是能力不变规则之外明确允许的体态变化。
- 完成后的状态保存到玩家 NBT，并同步给其他玩家；重登/死亡保留已生效的表现，不恢复未完成的演出。若真实形态或白名单已不匹配，则清除状态。
- 模型不替换时，最终视觉效果取决于原模型骨骼与通用四足动画的兼容性。当前物品图标复用原版回响碎片和荧石粉贴图。

回归命令：`gradlew build runServer --offline --init-script tools/endgame-regression.init.gradle`。

## 本次验证

- 构建及 47 项单元测试通过。
- 138 项服务器运行时断言通过，覆盖两种形态的应用、重复使用拒绝、复原、形态切换清理、同形态重载保留、持久化及同步、未屏蔽能力实例不变、默认/指定模型与动画、配方产量和燃料，以及子形态切换、误配置拒绝和残留状态清理；新增覆盖默认/自定义身体与眼高缩放、配置保存同步、形态/能力重载和复原缩放。
- 隔离客户端启动检查通过：客户端 Mixin 成功加载，蝾螈/蝙蝠渲染器和两个方向的变形动画、通用四足待机动画均已加载。
- 尚未手动逐项观察游戏内完整运动、嘴部持物及多人旁观的视觉效果。

## Feral 判定审查（2026-10-24）

检查当前 SSC Java 中 `getBodyType`、`FERAL`、`getPlayerForm_Render` 的调用点及附属的能力/限制入口：

| 链路 | 野性化后的行为 |
| --- | --- |
| MouthItemFeature、AdjustItemHoldFeatureRendererMixin、ExtraItemFeatureRenderer | 使用四足嘴部/背部持物表现，不改变物品使用权限或库存 |
| FeralPlayerEntityRendererMixin、DefaultModelAnimationSystem、AnimSystem | 使用四足渲染和动画；能力动画仍委托真实形态 |
| CapeFeatureRendererMixin、ElytraEntityModelMixin、PlayerEntityRendererFallFlyingMixin | 改变披风、鞘翅、飞行的显示姿态，不授予飞行能力 |
| PlayerEntityPoseMixin | 按要求启用四足游泳姿态及对应碰撞判定 |
| KeyboardInputMixin | 读取 LockPoseToStand 原始标记；包装形态委托原始 flags，不新增锁定 |
| TaCZ_Anim / TaCZ_AnimThird | 取消玩家动画管理器中的射击/换弹/近战/拔枪动画，不取消枪械事件本身；仅静态检查，未安装 TaCZ 实测 |
| ScareSkeletonMixin | 瞄准修正仍检查真实形态体型；恐惧与目标选择仍检查 SCARE_SKELETON Power，不因外观新增免敌能力 |
| 附属背包、工具、交互限制及技能树 | 读取真实形态/原有能力状态，不使用新增的渲染 Feral 标识 |

当前检查范围内未发现新增 Feral 标识可绕过上述能力或交互限制。第三方模组自行读取渲染形态的行为不在此结论范围内。

## 爬行兼容

蝾螈 `CrawlingPower.tick()` 会持续重写眼高，与野性化不兼容。现通过蝾螈配置的 `remove_powers` 移除 `shape-shifter-curse:form_axolotl_3_crawling`，复原后添加回去；已删除此前的 `FeralCrawlingScaleMixin` 及注册，不再修改爬行能力内部逻辑。当前蝾螈 eye_scale=0.3 保留。

实现参考 `PrimalPowerReconciler`，在服务端主线程按 (power ID, origin source) 动态移除与恢复并同步。回归覆盖能力移除、复原、重新加载能力、其他来源保护、存档记录恢复、配置增删屏蔽和切换形态不泄漏旧能力。

## 蝾螈野性化独立模型

`axolotl_3.json` 已启用 `replace_model`，`model_origin` 为 `ssc-primalstinct:form_axolotl_3_feralization`。客户端配置 `ssc_form_model/origins.origin.ssc-primalstinct.form_axolotl_3_feralization.json` 引用同名导出模型、贴图及 colormask，沿用匹配骨骼的尾巴、头部鳃和眨眼控制器。它只是渲染资源，无需注册新能力形态或 Origin。复原后使用原蝾螈模型；动画仍为通用 Feral。

## 野性化 Power 动画映射

`power_animations` 独立于 `replace_animation`，将技能动画 ID 映射到玩家动画资源 ID，默认空对象。仅在野性化期间生效；未配置或资源缺失时委托原形态，复原后使用原动画。映射随玩家状态保存、同步，数据包 `/reload` 可更新映射。没有新增 Mixin，不改变攀附能力。

蝙蝠已配置：

```json
"power_animations": {
  "shape-shifter-curse:attach_side": "ssc-primalstinct:bat_3_feralization_attach_side",
  "shape-shifter-curse:attach_bottom": "ssc-primalstinct:bat_3_feralization_attach_bottom"
}
```

占位资源位于 `assets/ssc-primalstinct/player_animation/`，分别为 `bat_3_feralization_attach_side.json` 与 `bat_3_feralization_attach_bottom.json`，内容暂复制原蝙蝠对应动画。替换时保留文件名、内部动画名，保持循环动画，使用玩家动画骨骼命名。资源替换后重载客户端资源（F3+T）或重启。沿墙移动的 CLIMB 状态不由该映射处理。

## 命令切形态与诅咒之月

`isCursedMoonApplied` 是诅咒之月参与标记，最终形态未变身也会被置为 true。结晶只在该标记与 Before/After 变形记录同时存在、当前形态等于 After 时拒绝使用，不再仅因夜晚参与标记拒绝永久形态。`set_form` 切换会清除野性化；普通形态拒绝复原粉尘属于正常行为，重新野性化后可复原。回归覆盖蝙蝠演出中/完成后经命令路径切蝾螈、保存读取、重新野性化/复原，以及实际月变身仍被拒绝。
