# ssc-primalstinct

[Shape Shifter Curse](https://github.com/onixary/shape-shifter-curse-fabric) 的官方附属（addon）：将"本能"资源重写为可加减、分层级、可满值锁定的进度内核，配套野性形态（Feral）开局选择、渐进能力解锁与食性/背包限制。

设计文档：Heptabase 白板「官方附属：SSC-Primalstinct」（实施路线图见卡片 `00 实施路线图与卡片索引`）。

## 环境要求

- Minecraft **1.20.1** / Java **17** / Fabric Loom 1.9-SNAPSHOT（与 SSC 主工程一致的已验证组合）
- SSC 基线：提交 `14480ce7`（项目版本 **1.10.0**），以其本地 remap 产物方式依赖：

```bash
# 先在 SSC 主工程生成基线产物（仅开发期需要；产物为 build/libs 下的 1.10.0 jar）
cd /path/to/shape-shifter-curse-fabric
./gradlew remapJar
```

## 构建与运行

```bash
./gradlew build        # 产物：build/libs/ssc-primalstinct-0.1.0.jar
./gradlew runClient    # 开发客户端（run/client）
./gradlew runServer    # 独立服务器（run/server，首次需同意 eula.txt）
```

## IDEA 测试环境

项目经 Gradle 同步后即可在 IntelliJ IDEA 中直接测试，无需额外配置：

- **运行配置**（Loom 在同步时自动生成，位于 `.idea/runConfigurations/`）：
  - `Minecraft Client` — 开发客户端，工作目录 `run/client`，前置任务 Make（IDEA 增量编译）
  - `Minecraft Server` — dedicated server（`nogui`，控制台在 IDEA Run 窗口内），工作目录 `run/server`，已剔除客户端专属的 LWJGL 库
- **Mixin 开发**：Loom 已把 mixin 注解处理接入 IDEA 编译器（`.idea/compiler.xml`，自动生成 refmap），Mixin/Gradle 触发器可在 .java 内直接打断点
- **编码**：`.idea/encodings.xml` 固定项目 UTF-8（中文注释/语言文件防乱码）
- **服务器调试命令**：dev server 中 `Dev` 玩家默认无 op，在 IDEA 服务端控制台输入 `op Dev` 后即可使用 `/primalstinct debug get|set|reconcile|ssc`
- **run/server/mods**：dev 运行会加载该目录下的生产 jar（首次会经 FabricLoader 重映射并缓存到 `run/server/.fabric/tmp`）；当前放有一套与 SSC dev 环境一致的模组（fabric-api 0.92.11、trinkets、Jade 等）。注意勿与手动启动共用同一 run 目录（world session.lock 冲突会直接启动失败）
- 依赖变更后若运行配置报找不到类，先触发一次 Gradle 同步（右侧 Gradle 面板刷新）

### 通过 MCP 驱动 IDEA（自动化测试）

IDEA 内置 MCP server（默认 `http://localhost:64342/sse`，端口可用环境变量 `IDEA_MCP_PORT` 覆盖）。在 ZCode 未注册 `mcp__jetbrains__*` 工具时，用 [tools/idea_mcp.py](tools/idea_mcp.py) 直连：

```bash
# 列出运行配置
python tools/idea_mcp.py get_run_configurations '{"projectPath": "F:/MC Modding/Projects/ssc-primalstinct"}'

# 从 IDEA 启动 dev server 并取回输出（进程不会自动退出，需另行停止）
python tools/idea_mcp.py execute_run_configuration \
  '{"projectPath": "F:/MC Modding/Projects/ssc-primalstinct", "configurationName": "Minecraft Server", "timeout": 150000}' \
  --timeout 150

# 其他常用：get_file_problems（IDEA 检查报错）、search_in_files_by_text、execute_terminal_command
```

可用工具共 20 个（`tools/list`）。服务器进程用 `taskkill`（按 `devlaunchinjector` 匹配 PID）停止；经 Gradle 委托的运行在强停后 Gradle 任务会报非零退出，属预期。已验证：MCP 启动 → 87 模组加载 → 附属与 SSC 初始化 → `Done (6.089s)`。

### SSC 依赖接入方式（重要）

参照 SSC_Xu_Addon 的成熟模式：**SSC 在 dev 环境「仅编译、不进 classpath」**。

- **编译期**：`modCompileOnly` 指向 SSC 工程的 remap 产物 `build/libs/shape-shifter-curse-fabric-<版本>.jar`（`-PsscJar=` 可覆盖）；更新基线时先在 SSC 工程执行 `./gradlew remapJar`
- **运行期**：把该生产 jar 手动放入 `run/client/mods/` 与 `run/server/mods/`，由 FabricLoader 运行时重映射加载（与生产环境同路径）

原因（实测）：loom 对 classpath 上 SSC 的重映射类路径不含可选集成模组（trinkets 等）的类层次，SSC 调用其「继承自 MC 类的成员」（如 `TrinketInventory.size()`，intermediary 名 `method_5439`）不会被映射为 named 名，运行时这些模组在场即 `NoSuchMethodError`（客户端创建世界时 `_loadForm` 崩溃的根因）。mods 目录的 jar 走运行时重映射，带完整模组集知识，无此问题。

其余规则：所有带 Maven 元数据的 mod 依赖一律 `transitive=false`（避免 loom 子树重映射引入重复的旧版 fabric-loader 使 Knot 拒绝启动）；SSC 运行时模组集在 build.gradle 平铺显式声明（PAL/AdditionalEntityAttributes 在生产经 apoli jij 提供，dev 不展开 jij 需平铺）。SSC 的 Fabric mod ID 是 `shape-shifter-curse`；其内嵌 Origins 源码，环境不得另装真 Origins。

### 无头命令测试（RCON）

dev server 已启用 RCON（`run/server/server.properties`：端口 25575 / 密码 `primalstinct-dev`），配合 [tools/rcon.py](tools/rcon.py) 可在服务器启动后直接执行调试命令：

```bash
python tools/rcon.py "primalstinct debug roster" "primalstinct debug resolve shape-shifter-curse:ocelot_3"
```

## 当前框架内容（卡01 + 卡02）

- main/client 入口分离；客户端类不进入 dedicated server 加载链
- `adapter/ssc`：SSC 内部类的唯一引用点（`SSCAdapter`）
- `instinct`：占位状态内核（value/level/locked，开发初值；卡03/05 替换为 CCA 组件与真实内核）
- 管理员命令（权限等级 2）：
  - `/primalstinct debug get [player]` — 查看占位状态与 SSC 侧本能快照
  - `/primalstinct debug set value|level|locked <player> <值>` — 写占位状态
  - `/primalstinct debug reconcile [player]` — 钳制数值、重算等级、满值锁定演示
  - `/primalstinct debug ssc [player]` — 直接读取 SSC `PlayerFormComponent`/`InstinctUtils` 状态
- `mixin/ssc` 与 `mixin/vanilla` 双 mixin 配置（当前为空，注入点须先登记 [docs/MIXIN_INVENTORY.md](docs/MIXIN_INVENTORY.md)）

### 卡02：形态名单与数据包契约

- 数据目录 `data/<ns>/primalstinct/{forms/*.json, levels/default.json}`；字段：schema_version/form_id/selectable/order/fallback_form/base_powers/level_overrides/instinct_powers/diet_profile/sleep_profile
- 公共等级表（开发初值）：阈值 20/40/60/80/100，L0..L5，L5=满值锁定；每级仅声明 add/remove 增量，查询时累计展开，同级冲突报错
- 两阶段加载：reload 时结构解析（未知字段/类型错误/等级约束，错误带文件路径）→ SERVER_STARTED 与热重载后引用校验（FormID 存在、兜底链到普通主形态、power 在 Apoli 注册表）→ 全部成功才原子替换运行快照（revision 递增）；失败保留上一版
- 子形态按自身 FormID 查配置，缺省继承 master 链（`PrimalRosterManager.resolve`）
- 首发样本：`ocelot_3`（FERAL 主样本）、`bat_3_sub_avali`（赞助子形态，兜底 master `bat_3`）、`axolotl_3`（非 FERAL，验证名单语义）
- 调试命令新增：`/primalstinct debug roster`（revision/顺序/兜底/错误）、`/primalstinct debug resolve <form_id>`（含继承链）

### 卡03：玩家持久状态与同步协议

- **CCA 组件** `ssc-primalstinct:primalstinct`（`component` 包，`RespawnCopyStrategy.ALWAYS_COPY`）：持久 schemaVersion/selectionCompleted/selectedFormId/value/locked；level 由等级表派生不另存；rate/会话数据仅运行期。value 读写统一做有限数检查与 [0, 满值] 钳制（NaN 不落盘不传播）；读档后按"满值即锁定"不变量修复；读 NBT 不做库存搬移或 grant power
- **S2C 快照协议**（`network` 包，1.20.1 Identifier+PacketByteBuf 风格）：value/rate/level/locked/serverTick/revision；瞬间修改/跨级/锁定变化→立即同步；登录/复活/换维→事件钩子同步；增长期限频校正。客户端插值仅用于条形长度，规则判定一律服务端
- **客户端**（`client.network.ClientPrimalstinctState`）：快照缓存 + 断线清理；dev HUD（左上角）显示同步状态用于肉眼验证
- 卡01 的内存 Map 占位与 `set level` 命令已移除（level 派生化）

### 卡04：切断旧本能计算与显示桥接

- **首批 SSC Mixin**（`mixin/ssc`，配置 `ssc-primalstinct-ssc.mixins.json`）：
  - `InstinctUtilsMixin`：HEAD 取消 `serverTick`（旧增长/诅咒之月冻结/满值变身→清零整链，不只拦 checkThreshold）、`clearInstinct`、`addInstinctEffect`（两个重载——@Inject 处理器不捕获目标参数即可同名全覆盖）
  - `InstinctValueConditionMixin`：旧 `instinct_value` 条件恒 false（旧字段不再作为新系统输入）
- **注入模式**：目标为 SSC 自有类 → 类级 `remap = false` + 仅方法名匹配（SSC 成员名生产不变；true-feral-addon-modifier 的生产验证模式），MC 类型不进 refmap
- **金苹果/牛奶不受影响**：SSC ItemStackMixin 不经过被拦方法，旧抑制路径因写入废弃而自然失效，原版效果保留
- **旧锁不侵入新资源**：`playerInstinctLock`/诅咒之月冻结仅被已取消的旧 serverTick 读取；旧变身动画可继续用其视觉锁
- `instinct/PrimalstinctTicker`：接管后的附属服务端 tick 骨架（卡05 填充增长内核）
- HUD 审计（卡15 前置）：SSC HUD 链为 `InGameHudMixin → InstinctBarRenderer`，会被法力条 `OverrideInstinctBar` 顶替——新 HUD 须独立渲染且不受 NoInstinct 隐藏判定影响（不删 flag）；当前 dev HUD 左上角无冲突
- 调用点核查记录见 [docs/MIXIN_INVENTORY.md](docs/MIXIN_INVENTORY.md)（S1/S2/S5 已实施）

### 卡05：本能数值、等级与满值锁定内核（阶段 A 收尾）

- **纯函数内核** `PrimalstinctKernel`（无 MC 依赖，10 个单元测试）：modify 事务（钳制/等级依据/锁定/dirty 同事务）、modifyMulti（tick 分来源结算）、mergeBySource（锁定时仅保留负向外源）、点/秒→点/tick 换算、NaN/无穷拒绝
- **来源分级** `PrimalstinctSource`：POWER（一切 Apoli action 默认归属，JSON 不可声明来源绕锁）/ ITEM_RECOVERY / WORLD_RECOVERY（卡14 入口）/ ADMIN（调试命令）；满值锁定后 POWER 正负皆拒（Power Action 不能自解锁），外部合法负向使 value<max 立即解锁，满值处外部正向 no-op
- **Service** `PrimalstinctService`：统一 modify/setRate/removeRate（稳定键去重，卸载即失效）；tick 顺序＝基础增长（名单内形态自动持有 BASE_RATE_KEY=100/9000 点/秒，满值即停）→分来源合并→事务应用→跨级/锁定 syncNow→（卡06 能力差量挂载点）
- **速率语义**：统一点/秒、tick 除以 20；运行期速率不落盘，断线清理、重登重算；不照搬旧 calcRate 查询递减副作用（duration 策略待确需时再精确定义）
- **实测回归**：曾发现 tick 把合并增量统一按 POWER 结算导致负向外源被拒改误伤（解锁失效）——已改分来源事务并用单测+联机复测锁死
- 调试命令：`rate set <player> <key> <点/秒>` / `rate remove` / `rate list`；dev HUD 显示快照值+外推值+速率
- **验证**：10/10 单测通过；联机闭环实测（quickPlay 自动连入 dev server + RCON）——100/locked → -20/s 解锁降至 L2 → 移除稳定 → 50/s 爬升跨级 → 100 重锁 → 锁定时正向 no-op → 速率清空

### 卡06：统一形态与等级 Power 的挂载结算

- **全部走 SSC 公开 API，零新增 Mixin**（官方附属策略：`SSCEvent.FORM_CHANGE_END`/`ACCESSORY_EQUIP/UNEQUIP` 事件、apoli `PowerHolderComponent` 的 source 级 API 天然够用）
- **`PowerPlanResolver`**（纯函数，5 个单测）：期望能力唯一计算点——base_powers → L1..当前级累计展开（每级先公共表后 form 覆盖，remove 先行、后级可撤销前级）；授予 source=`ssc-primalstinct:form_base`/`level_N`（N=最终引入级，跨级往返稳定）
- **`PrimalPowerReconciler`**：事件（换形态/饰品/登录/复活/reload/跨级）合并为 tick 末主线程结算；差量以 (powerId, sourceId) 为身份只增删附属来源，不触碰 origin/子形态/饰品来源——共享同名 power 不误删；屏蔽 SSC 原能力用 origin source（`getFormLayer().getRight()`，实测命名为 `form_<id>`），卸屏蔽靠 `_loadForm` 的 setOrigin 双写重建基线，不盲加回；无 new Thread+sleep 兜底（注册表就绪主线程直结，缺失报错跳过）
- **实测**：`set_form ocelot_3` → form_base=cat_friendly + SSC 37 个 origin power 原样保留；跨级序列 L2→L3→L4→L0→L2：digging/attack 按表授予、L4 撤销 digging、L0 清空保留 form_base、重复设置不叠属性；期间发现命令路径漏挂 reconcile 触发（已统一到 onChanged 出口）
- 观测命令：`/primalstinct debug powers <player>`（按来源列出，含 origin 来源）；SSC 换形态命令为 `shape_shifter_curse set_form <target> shape-shifter-curse:<form_id>`

### 卡07：本能 Power、Action 与条件（Apoli 工厂）

- **`modify_primalstinct_rate` Power**（`power.factory` 包）：字段 source_id/rate_per_second + condition；Service 每 tick 扫描活跃实例向内核贡献速率（稳定键 `power:<source_id>`），失活/撤销自动移除——无"获得即改值"路径，重挂载不刷值
- **`add_primalstinct` entity action**：字段 amount，统一 POWER 来源（锁定时按内核规则拒绝）；只挂真实事件回调（apoli `action_on_item_use` 进食、`action_on_hit` 战斗——2.9.2 内置事件无需新工厂）；ActionFactory 不套 allowCondition
- **三条件**：`primalstinct_value`（锁定恒 false）、`primalstinct_level`、`primalstinct_locked`（comparison/compare_to 与 apoli 条件同构）
- **同步降频**：限频校正增加 rate 变化检测（来源/速率/数值/等级/锁定任一变化才补发）
- **ocelot_3 三样本**（`data/ssc-primalstinct/powers/`）：丛林环境持续源（apoli:biome is_jungle +0.02/s）、生肉进食瞬时（+2.0）、战斗命中瞬时（+1.0）——开发初值
- **实测**：丛林内 merged=base+0.02、离开回落；锁定后两正向源显示 `[suppressed: locked]` 且 merged=0；同形态 reload 值纹丝不动；`debug sources` 观测命令落地

### 卡08：snow_fox_3 基础能力与食性（测试样本自本卡起由 ocelot_3 切换）

- **测试样本切换**：snow_fox_3 同为 FERAL 最终形态但无 ocelot 的生肉/裸爪既有加成，观测更干净；ocelot_3 样本保留作回归
- **能力分工**（避免与 origin 重复叠加——snow fox 自带 fox_friendly/坠落攻击/空中速度等）：form_base 仅加 `always_harvest`（`apoli:modify_harvest`——"可采集且有掉落"与"挖得快"分离，后者仍由公共等级表 L2 的 barehand_digging_speed_up 提供）；战斗由形态自身坠落攻击+公共表 L3 barehand_attack_up 覆盖
- **三档食性**（行为全由 Power 驱动，物品清单收敛到物品标签避免清单重复）：
  - normal（可吃且本能降低，恢复路径）：`action_on_item_use` + `add_primalstinct(-1.0)`，标签含 raw_meat 引用/浆果/熟肉
  - unsuitable（可吃但本能上升）：同结构 `+1.0`，面包/作物/苹果系
  - forbidden（**可吃但无营养**，与 SSC 原版 raw_meat_only 同语义）：`apoli:modify_food` set_total 0——按用户决定不做成完全不可食用
  - 标签：`data/ssc-primalstinct/tags/items/snow_fox_diet_{normal,unsuitable,forbidden}.json`；禁止档仅真正有毒食物（生肉保留在 normal，避免同物品跨档冲突）
- **diet 数据契约落地**（`primalstinct/diets/*.json`）：normal/unsuitable/forbidden 各含 instinct_delta + item_tag；加载器并入名单管线（结构校验+原子交换）；diet 引用软校验（缺失告警不阻断，旧样本档案逐步补齐）；`debug resolve` 展示档案解析结果
- **实测**：名单 [15] snow_fox_3 就位；resolve 显示三档档案；set_form 后 form_base 挂载 always_harvest + 三食性 power；基础增长源激活；三份软校验告警按预期输出
- **待 playtest**：吃正常/不适/禁止食物的实际三档行为、L0 裸爪采集掉落、多玩家食性隔离（Power 按玩家授予，结构上已隔离）；SSC CustomEdibleUtils 缓存刷新在实体捕食（卡18）接入时处理

### 卡09：背包与快捷栏限制及物品保全（发布门槛，风险最高步骤）

- **方案定案**：虚拟占位（客户端占位绘制 + 服务端锁槽规则，不放真实占位物品）+ 正规插入 API 集中拦截——与用户确认：一般 mod 兼容仍在范围（fail-open 风险由 insertStack/addStack/offer 覆盖正规路径），实体占位的防泄漏钩子与守恒账本复杂度不做首发；规则引擎与呈现解耦，后续可逆
- **`InventoryLockRule`**（纯函数）：快捷栏 0–8 / 主背包 9–35 真实索引；多 Power 各维度独立取 max（升级放宽）——实测修复了整体 max 合并把互补 Power 互相解锁的 bug
- **Power**：`restrict_hotbar`/`restrict_inventory`（allowed_slots）；snow_fox_3 渐进样本 L0=1/0 → L2=3/9 → L4=5/18（max 合并免 remove）
- **`InventoryLockManager`**：规则随 Power 结算派生；规则变化时——光标先存合法状态、锁定槽物品入可用槽余者按原槽索引暂存（组件 stashSlots NBT，-1=光标）、解锁优先还原原槽；不自动扔出、不逐 tick 扫描。死亡：keepInventory=false 暂存于死亡位置掉落一次并清空（ALLOW_DEATH 先于重生复制，无复制）；true 随组件保留
- **首批 vanilla Mixin**（4 个，mixin/vanilla）：Slot 层 canInsert/canTakeItems 闸门（覆盖容器转移/拖拽放置/双击收集）、ScreenHandler 点击级防御（数字键交换/丢弃/中键/拖拽 stage 1 挡锁定槽）、PlayerInventory.insertStack 定向插入（捡物//give/正规 mod）、锁定快捷栏槽拒绝选中并回发校正
- **实测**：L0 暂存（原槽索引）→ L2 还原原槽 → 降级再暂存，三轮往返守恒；受限 give 落地不掉入锁定槽（插入路径拦截验证）；死亡暂存掉落一次+重生无复制；规则跨重生正确重派生。GUI 交互（点击/拖拽/滚轮/数字键/双容器）待用户 playtest
- 观测命令：`/primalstinct debug inv <player>`（规则/库存摘要/暂存/派生探针）
- **UI 反馈（playtest 后补）**：S2C 快照扩展 allowedHotbar/allowedMain 字段，JOIN/规则变化即时下发——① True Feral 式**紧凑快捷栏**（受限时裁剪 widgets.png 只渲染允许槽、选择框映射紧凑坐标、允许 0 槽时整条隐藏；物品锚点对齐原版 barX+i*20+3 / barY+3）；② 背包/容器界面锁定槽叠加**深灰半透明遮罩**（复用原版槽位底图）；③ **滚轮客户端跳过**（只在允许槽间移动并正确回绕，消除"本地选择→服务端回弹"的高亮跳变）；数字键仍由服务端拒绝+回发校正（卡16 UI 层再平滑化）

### 卡10：装备掉落与交互限制 Power

- **6 个新 Power 工厂**：`lock_equipment`（盔甲 36-39）/`lock_offhand`（40）——并入 InventoryLockRule（OR 合并，等级 remove 显式解除）；`prevent_inventory_crafting`（仅 2×2，`include_crafting_table` 独立配置 3×3）；`prevent_door`（门/活板门/栅栏门类覆盖，床保持可用）；`prevent_block_place`（BlockItem 放置，可选 item_tag 范围）；`drop_tool_after_use`（成功行为后掉真实剩余堆栈）
- **装备锁成对掉落**：规则收紧/再次装备时"清空原槽+原地掉落"成对且只一次，40t 拾取延迟防循环；GUI 路径由 Slot 闸门自动覆盖，右键穿戴/发射器路径注入 **PlayerEntity.equipStack 具体实现**（LivingEntity 声明为抽象方法不可注入——首版踩坑实录）
- **合成禁用**：结果槽点击守卫（CraftingResultInventory 判定）+ 配方书 `onCraftRequest` 拦截，2×2 与 3×3 分开关
- **工具掉落三钩子**：`attack` TAIL（命中才进）、`tryBreakBlock` RETURN true（挖掘取消/开始不进，天然区分）、`useOnBlock` isAccepted（铲地/剥皮/耕地成功）；掉真实堆栈（耐久已结算），槽位身份不匹配宁可不掉不错丢；被 prevent 类拒绝不算成功使用（优先级约束）
- **snow_fox_3 渐进样本**：L0 全锁（装备/副手/合成/门/放块/工具掉落）→ L2 解装备锁 → L4 解放块
- **实测**：服务器干净启动（7 个 vanilla mixin 全部应用）；form_base 12 powers 含 6 个新工厂全部挂载；规则 1/0+装备锁派生正常。行为验收（剑命中掉落/挖掘掉落/装备无喷射/门放块拦截）待用户 playtest

### 卡10 经验教训（playtest 修复实录）

**问题**：合成/装备/门/放块拦不住 + 放方块出幽灵方块（客户端预测放置、物品不消耗）。修完后确立的交互拦截架构：

1. **拦截深度决定成败**：包级 HEAD cancel（`onPlayerInteractBlock`）跳过了原版自身的回滚同步——深层拦截（`ServerPlayerInteractionManager.interactBlock`）让原版的 delta-sync/方块校正逻辑自然走完，`ActionResult.FAIL` + `syncState()` 即可
2. **客户端预测拒绝是必须的**：仅在服务端拒绝 → 客户端幽灵方块/幽灵物品。`ClientPlayerInteractionManager.interactBlock` HEAD 返回 FAIL → 客户端从不预测 → 无需任何回滚。双层（客户端+服务端）缺一不可
3. **共享判定**：`InteractionRestrictions`（`interaction` 包）在客户端/服务端两侧引用同一逻辑——判定漂移=行为不一致
4. **合成拦结果计算而非结果提取**：`CraftingScreenHandler.updateResult` HEAD cancel（结果不产出，拿不出也 shift 不出），配合 `ScreenHandler.onSlotClick` 守卫兜底
5. **注意**：`ScreenHandler.onSlotClick` 才是服务端点击入口（`internalOnSlotClick` 是内部路径）；`LivingEntity.equipStack` 是抽象方法（注入具体实现类）；删工厂后残留 profile 引用 → 名单校验整体回退 revision 0
6. **盔甲限制用 Apoli 原生** `apoli:restrict_armor` power（SSC form_disable_*_armor 四件套直接引用），自研 lock_equipment 已移除

### 卡10 增补：加工方块与容器禁用

- `prevent_processing_blocks`：工作台/熔炉系（普通/高炉/烟熏炉）/酿造台/切石机/织布机/制图台/砂轮/锻造台/铁砧——右键不弹界面
- `prevent_containers`：箱子/trapped 箱/末影箱/木桶/潜影盒/漏斗/发射器/投掷器——右键不弹界面
- 均走 InteractionRestrictions 共享判定 → 客户端预测拒绝 + 服务端权威双拒绝（同门/放块架构）

### 卡12：首次选择的服务端事务

- **`SelectionSessionManager`**（`selection` 包）：服务器掌握 pending 状态与选择结果；JOIN 后检查 `selectionCompleted`（新玩家/首次安装 → pending）；断线清会话、重连恢复 pending
- **S2C**：有序 FormID 列表 + revision + 会话 nonce + 默认项（名单来自卡02 手工配置，不按 FERAL/子形态过滤）
- **C2S confirm**：只带 FormID + revision + nonce；服务端主线程校验 pending、名单、revision、nonce——客户端不可传 value / 已完成状态 / 赞助凭据
- **确认事务**：`_loadForm` 变形 → 验证实际最终 FormID（TransformManager 事件可能改目标，以实际为准）→ 初始化本能（value=0）→ 写 selectionCompleted → 解除 pending → 回发确认
- **幂等**：非 pending / 过期 nonce / revision 不匹配 / 不在名单 → 均拒绝，不重复变形
- **管理员恢复**：`/primalstinct debug select <player> <form_id>` 强制完成选择
- 空名单：明确报错 + 管理员命令兜底；不越权选赞助形态、不无限卡屏
- 客户端 UI（选择界面）在卡13 实现；当前 RCON `debug select` 可验证事务流程

## 包结构与路线图映射

| 包 | 职责 | 卡片 |
|----|------|------|
| `adapter.ssc` | SSC 内部类集中引用 | 01 |
| `component` | 玩家持久状态（CCA 组件，唯一数据源） | 03 |
| `data` | 形态名单/等级/食性数据包加载 | 02 |
| `instinct` | 本能资源内核（数值、等级、满值锁定） | 05 |
| `power` | 形态与等级 Power 统一挂载结算 | 06/07 |
| `inventory` / `interaction` / `sleep` | 背包限制 / 装备与交互限制 / 蜷缩睡眠 | 09/10/11 |
| `selection` / `network` | 首次选择会话与同步协议 | 12/13 |
| `client.ui` | HUD、选择界面、预警表现 | 13/15/16 |
| `mixin.ssc` / `mixin.vanilla` | SSC 目标 / 原版目标注入 | 04/09/10/11/15/17 |

数据目录（卡02 建立）：`data/ssc-primalstinct/primalstinct/{forms,levels,diets}`；Apoli 能力仍在 `data/<namespace>/powers`。贴图与本地化在 `assets/ssc-primalstinct/`。
