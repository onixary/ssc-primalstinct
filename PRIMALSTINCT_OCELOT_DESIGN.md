# Ocelot 初版能力配置

依据 Heptabase 白板「Primalstinct 能力设计」2026-09-08 的两张卡：
`fc73ebdd-8a0e-483c-8cbd-d9d53f571f2b`、`5c1d5329-ae85-4daf-85b2-60a956d9b203`。

初始阶段保留，编号为 1。全局区间为 L1 `[0,25)`、L2 `[25,50)`、L3 `[50,75)`、L4 `[75,100)`，100 点为 L5 锁定态。存档保留实际本能值，重新按区间计算等级。没有更改本能条贴图。

入口：`src/main/resources/data/ssc-primalstinct/primalstinct/forms/ocelot_3.json`。
每级 Power 位于 `powers/ocelot_3/level_N/`，由 `level_overrides` 成组替换；降级会重新授予对应级的完整能力。
旧的 `ocelot_3_instinct_overheating_test`、`ocelot_3_wander_ai` 文件保留供手动测试，已从该形态的挂载列表移除。

## 白板明确数值

| 项目 | L1 | L2 | L3 | L4 | L5 锁定 |
|---|---:|---:|---:|---:|---:|
| 主背包 / 快捷栏 | 18 / 5 | 18 / 5 | 12 / 3 | 9 / 3 | 0 / 1 |
| 副手禁用 | 否 | 否 | 是 | 是 | 是 |
| 盔甲禁用 | 否 | 否 | 否 | 是 | 是 |
| 背包合成禁用 | 否 | 否 | 是 | 是 | 是 |
| 交互方块失败 | 0 | 20% | 35% | 65% | 100% |
| 容器失败 | 0 | 0 | 20% | 50% | 100% |
| 工具成功使用 / 挖掘后掉落 | 0 | 0 | 25% | 40% | 100% |
| 武器成功攻击后掉落 | 0 | 0 | 0 | 40% | 100% |
| 无输入接管等待 | 无 | 无 | 30 秒 | 15 秒 | 8 秒 |
| 过热增速（每目标/秒） | 无 | 无 | 无 | 2.5 | 5 |
| 过热增速上限（/秒） | 无 | 无 | 无 | 20 | 20 |
| 过热衰减（每秒） | 无 | 无 | 无 | 2.5 | 5 |
| 过热持续时间 | 无 | 无 | 无 | 5 秒 | 20 秒 |
| 图鉴 / 告示牌乱码比例 | 0 | 0 | 15% | 35% | 100% |

过热为内置隐藏的 0–100 触发计量条（`instinct_overheat`，不持久化、不可视化）：每 10 tick 以 8 格内代理原生可攻击目标计数结算，每个目标按等级增速贡献增长，无目标按等级速度衰减至 0；满 100 清零并施加过热 Buff，Buff 存在期间计量冻结（不增不衰）。创造模式不接管。满值锁定不再阻止 AI；锁定演出期间暂缓接管。
L5 禁止放置方块，调色界面文字全乱码，物品名称与提示统一为 `…?`，饥饿值大于 6 时每 2 秒恢复 1 点生命。

白板歧义的暂定处理：L3 的“禁用容器”与“20% 失败”冲突，采用 20%。L3 的采掘与食物“与 lv1 一致”按“不在本级继续提高”处理，保持 L2 数值。

## 未指定数值的初版增益

以下增益均可在各级 JSON 调整；不是白板已定的平衡数值。食物沿用 SSC 的 `raw_meat` 标签（含生鱼与已有模组兼容项）。

| 项目 | L1 | L2 | L3 | L4 | L5 |
|---|---:|---:|---:|---:|---:|
| 普通移动属性增幅 | 10% | 10% | 20% | 30% | 45% |
| 起跳速度增幅 | 10% | 20% | 30% | 40% | 50% |
| 额外护甲点 | 2 | 4 | 6 | 10 | 14 |
| 额外最大生命点 | 0 | 2 | 4 | 8 | 12 |
| 空手额外伤害 | 0 | 2 | 4 | 6 | 8 |
| 空手采掘倍率 | 1× | 4× | 4× | 7.5× | 10× |
| 食物额外饥饿点 | 2 | 3 | 3 | 4 | 6 |
| 食物饱和度参数增量 | 0.1 | 0.2 | 0.2 | 0.3 | 0.5 |

L5 空手采掘倍率高于 SSC Bat_3 的 `barehand_digging_speed_up`（7.5×）；不额外改变方块采收等级。代理本身保留移动倍率 1.5、游荡 chance 20、跳跃高度倍率 2。
L2 起，16 格内符合代理攻击类型的实体显示红色穿墙轮廓。轮廓与过热检测均忽略视线，范围内即计入。此检测表示“附近可攻击对象”，并不要求代理已经选中或追击目标。
遮蔽了 SSC 原本在所有阶段提供的伤害、生命、食物、跳高、盔甲禁用及 no_shield，改由等级控制。其他原形态能力保留。

## 新增和扩展的 Power

- `ssc-primalstinct:interaction_failure`：`block_chance`、`container_chance`，范围 0–1。仅服务端掷骰；同 tick 同位置复用结果。容器单独计概率，不重复掷交互概率。包括原版交互方块、带菜单方块、实现 Inventory 的方块实体；其他模组方块可加到 `ssc-primalstinct:interactive_blocks` 方块标签。
- `ssc-primalstinct:drop_tool_after_use`：新增 `chance`（默认 1）、`on_attack`（默认 true）。成功采掘、工具对方块/实体使用、实际近战命中、弓弩发射后处理真实剩余堆栈。取消挖掘、失败交互、空挥不触发；堆栈身份变化或已经耗尽时不掉落别的物品。三叉戟投掷沿用原版将物品变为投射物的行为，不额外复制物品。
- `ssc-primalstinct:restrict_inventory`：新增 `lock_equipment`（默认 false）。锁定槽中的真实物品清空并掉落到角色身边，带 2 秒拾取延迟；不搬移到其他槽、不暂存。旧版本暂存物品在规则结算时一次性掉落。
- `ssc-primalstinct:instinct_perception`：`codex_chance`、`palette_chance`、`sign_chance`（默认 0）；`item_text`（默认空字符串，即不替换）；`exempt_item_tag`（默认 `ssc-primalstinct:readable_items`）；`target_radius`（默认 0，即不描边，最大 128）；`proxy_entity`（默认 ocelot）。各功能可独立开关，支持 Apoli condition。
- `ssc-primalstinct:passive_until_provoked`：无字段。生物不主动仇恨持有者，被打后才仇恨反击。经 `TargetPredicate.test` 头部注入实现：目标为持有者且该生物 lastAttacker 非该玩家时判否，屏蔽一切以 TargetPredicate 主动选玩家为目标的 AI（含模组生物与幻翼/末影人自建谓词）；RevengeGoal 还击在受击时因 lastAttacker 已指向攻击者而放行，仇恨维持走 shouldContinue 的原版脱战逻辑。脑 AI（监守者）与直接 setTarget 的特殊路径不受影响。当前挂载于 L5 `level_5/aggro`。
- `ssc-primalstinct:instinct_overheat`：`radius`（默认 8）、`check_interval`（默认 10 tick）、`growth_per_target`（每目标每秒增速）、`max_growth_per_second`（总增速封顶，默认 20，防大量目标连环触发软锁）、`decay_per_second`（无目标每秒衰减）、`duration_seconds`（触发后 Buff 秒数）、`proxy_entity`（默认 ocelot）。Power 自身每 check_interval tick 结算计量（0–100，存于实例、不持久化）；检测复用代理原生目标谓词并忽略视线（范围内即计入）；满值清零并施加 `instinct_overheating` Buff（强制接管的开关不变）。Buff 期间计量冻结。旧随机触发方案（`apoli:action_over_time` + `proxy_has_nearby_attack_target` 掷骰）保留在 `ocelot_3_instinct_overheating_test` 供手动对照。

文字和轮廓只在持有者客户端绘制，不修改告示牌、物品 NBT、服务端名称或其他玩家视角。服务端每 10 tick 同步效果与目标；移除 Power 后最迟约 0.5 秒恢复。乱码稳定选择受影响的字符，并使用原版 `Style.withObfuscated(true)`（`§k`）动态绘制，保留原字符和其他样式。文字钩子位于原版与 SSC 缩放渲染器共用的字符绘制入口，覆盖书本正文、标题、按钮和详情页。告示牌双面、发光文字与悬挂告示牌共用渲染入口。`sedative_fragment` 始终豁免，移除标签也不会隐藏；其他物品可加到豁免标签。

随机交互失败与必然禁止使用不同 actionbar 文案，随机掉落与必然掉落也分别提示。

## 验证

执行 `gradlew.bat test remapJar --offline`；新增等级边界、升降级能力替换、白板时间和库存数值、文字稳定性/比例测试。未启动 Minecraft，实机效果由用户验证。


### Static obfuscation (2026-09-09)

Instinct scrambling now selects a stable replacement from the active font's vanilla `charactersByWidth` obfuscation pool. Character position and original code point determine the selection; shadow and normal passes use the same glyph. This overrides the earlier animated `obfuscated` behavior for instinct text only. Font resource reloads may change the available glyphs. Original text, styles, and glyph advances remain intact.
