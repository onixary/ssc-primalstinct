# 方块 GeckoLib 项目

本目录包含 7 种终局方块的 10 个状态项目，全部使用 **GeckoLib Animated Model / Block** 格式，配套 Minecraft 1.20.1、GeckoLib 4.8.4。

## 资源位置

以下路径相对于 `src/main/resources/assets/ssc-primalstinct/`。

| 内容 | 路径 |
| --- | --- |
| 几何体 | `geo/block/<项目名>.geo.json` |
| 动画 | `animations/block/<项目名>.animation.json` |
| 基座基础贴图 | `textures/block/primal_pedestal_moss.png` |
| 基座激活贴图 | `textures/block/primal_pedestal_moss_fulfilled.png` |
| 基座激活发光遮罩 | `textures/block/primal_pedestal_moss_fulfilled_emission.png` |
| 其余方块图集 | `textures/block/gecko/<项目名>.png` |

贴图均为 32×32。基座保留用户转换后的几何体和基础/发光贴图；祭坛三态、转化台与传送门框架已换成正式几何体；导线、门面仍保留已有几何体，将原有 16×16 贴图无缩放合并到 32×32 图集中并调整 UV。

基座每个状态包含 22 个立方体，最短轴为 0.75；接收口内径 10×10、深 4，四面供品框净空 7×7、中心高度 7.5。GeckoLib 坐标以 X/Z 中心为原点、Y=0 为底面，单方块范围为 X/Z [-8,8]、Y [0,16]。

祭坛三态共用同一套 32×32 图集布局：`primal_altar` 与 `primal_altar_active` 各 13 个立方体，几何体与 UV 逐字节一致，只有贴图不同；`spent_primal_altar` 是独立的破损几何体，20 个立方体——基座与塔身东南角被啃掉一块、北侧碗沿豁开 4 单位、东侧碗沿塌掉半截、碗内台阶缺一角、核心裂成两半。破损几何新切出来的断面统一从图集 (16,22)–(32,32) 的生石噪声区按 1 单位 = 1 像素取样；其余面沿用普通态的 UV 矩形并按其占原面的比例切出子矩形，因此三个状态都不会拉伸。

传送门框架参考基座的三段式：16×1.5 苔石脚、15×5 石身、16×1.5 收边，共 7 个立方体。上半不是基座那种围成接收口的空心边框，而是四根 7×8×7 的方形石柱，柱间留 2 单位缝隙，俯视是四宫格。**纵向渐变靠 UV 实现**：侧面直接取 u = 横向坐标 + 8、v = 模型 Y，所以贴图纵轴就是模型高度，柱子自然接着石身的色走，接缝不会断色——贴图 0~15 行是苔石到黑曜石的单调变暗（亮度 110 → 23），柱子占 8~15 行。

转化台是**下半砖**：轮廓与碰撞体都是原版半砖的 16×8×16，台面上方 1 单位悬浮一圈符文阵。符文阵由两层正八边形环（外环外接半径 16，即 1 格；内环 11）加四个方位的符文板组成，共 20 个立方体，所以模型在 X/Z 上会伸到 ±16、比方块边界各挑出半格——这是刻意的，测试里对 `CONVERSION_PLATFORM` 单独放宽到 ±16。八边形的四个正交边是轴对齐的，四个斜边绕 Y 轴旋转 45° 的整数倍；立方体旋转的写法见 `tools/lib/bbmodel.js` 顶部说明。石材压到祭坛约六成明度并往中性灰偏，符文用祭坛激活态的红色系。

## Java 接入

`EndgameGeoBlockEntity` 提供动画实例缓存。基座、祭坛与转化台继续使用各自原有方块实体、NBT 和服务端逻辑；导线、失效祭坛、门框和门面使用无 ticker 的 `EndgameModelBlockEntity`。

`EndgameGeoBlockRenderer` 负责场景渲染，关闭原版模型绘制以避免重叠。`EndgameBlockVisual` 按 `FULFILLED` / `ACTIVE` / `LIT` 选择资源。供品图标和祭坛奖励预览通过 `renderFinal` 绘制，避免重复应用模型变换。传送门使用透明渲染层。

物品使用 `EndgameGeoBlockItem` 和 `EndgameGeoItemRenderer`，对应 `models/item/*.json` 的 `builtin/entity` 父模型，保留物品栏、地面和手持变换。旧 `models/block/*.json` 与 blockstates 继续提供原版粒子等资源引用；场景几何体由 `geo/block/` 提供。

旧存档中原本没有方块实体的四类方块，在服务端区块加载时由 `EndgameModelMigration` 补建渲染实体；仅扫描包含这些方块的区段，保留已有实体。

## 面剔除

用 BER 渲染的方块**必须自己给出非完整的剔除形状**，否则相邻方块会把贴着它的那一面整个剔掉。

`Block.shouldDrawSide` 判的是「邻居的剔除面有没有完全盖住自己这一面」，而剔除面来自
`extrudeFace(getCullingShape(), direction)`。默认实现直接返回**整格**，所以模型哪怕空着大半格，
邻居也照样被剔；模型是 BER 画的、缝看得见，于是边上就露出一个洞。

注意 `isShapeFullCube` 要求形状**完全等于**满方块（`!matchesAnywhere(fullCube, shape, NOT_SAME)`），
不是看包围盒——所以带缺口的并集形状就算非完整。

| 方块 | 做法 |
| --- | --- |
| 基座 / 导线 / 转化台 | 覆盖 `getOutlineShape` 为非完整形状（同时改碰撞） |
| 门面 | `nonOpaque()` |
| 传送门框架 / 祭坛 / 失效祭坛 | 覆盖 `getCullingShape`，只开口剔除、碰撞保持整格 |

后三个之所以只改剔除形状：框架四柱之间的缝有 8 格深、祭坛塔身也远窄于整格，直接改碰撞会造出
掉进去爬不出来的口袋。剔除形状比模型略大是安全的（少剔几面而已），偏大才会错误地吃掉邻居的面。

## 局部发光

`EndgameEmissionLayer` 查找基础贴图同目录下的 `<基础贴图名>_emission.png`。遮罩与基础贴图的尺寸、UV 必须一致；透明像素不绘制，非透明像素按遮罩自身颜色进行全亮叠加。没有遮罩时跳过该层，资源包重载后可发现新增遮罩。

当前已接入用户提供的基座激活遮罩、祭坛激活遮罩 `primal_altar_active_emission.png`（腰线、碗沿、碗内光池与核心）和转化台遮罩 `primal_conversion_platform_emission.png`（符文阵的刻痕与符文板）。其他方块可按同一规则添加，例如 `textures/block/gecko/primal_energy_wire_lit_emission.png`。表面自发光与照亮周围环境是两回事，环境光照仍由方块的 `luminance` 控制。

## 后续编辑

1. 编辑并保存同名 `.bbmodel`。
2. 导出 GeckoLib 几何体到同名 `.geo.json`，保持 GeckoLib 4 支持的格式版本；当前使用 `1.12.0`。不要用新版 Bedrock 默认版本直接覆盖。
3. 单独保存基础贴图和发光遮罩 PNG。保存工程不会自动更新游戏资源。
4. 普通/激活状态保持几何体和 UV 一致，分别更新对应状态资源。
5. 当前动画 JSON 为空，Java 不注册动画控制器；添加动画时再配套控制器与动画名称。

`tools/` 下的脚本一览：

| 脚本 | 作用 |
| --- | --- |
| `lib/bbmodel.js` | 由立方体列表写出 `.geo.json` 与 `.bbmodel`，含旋转立方体的写法 |
| `lib/png.js` | 最小 PNG 读写 + 画布 |
| `gen_altar_textures.js` | 由普通态贴图派生激活/失效贴图与发光遮罩 |
| `gen_altar_models.js` | 由 `primal_altar.bbmodel` 派生三个状态的 geo，以及另两个 `.bbmodel` |
| `gen_conversion_platform.js` | 转化台的几何体、贴图与发光遮罩 |
| `gen_portal_frame.js` | 传送门框架的几何体与贴图 |
| `normalize_geo.js` | 从 Blockbench 导出之后的收尾：修 `format_version` 与排版 |

**`gen_altar_textures.js` 与 `gen_altar_models.js` 默认不会覆盖已经存在且内容不同的文件**，只会打印「跳过」。祭坛的激活/失效状态已经被手工编辑过，脚本重新生成的内容和磁盘上的不一致，静默覆盖会丢工作；确实要重新生成时显式加 `--force`。转化台是完整生成物，没有这个限制。

`gen_altar_models.js` 会顺带重算普通态的 `.geo.json`——`.bbmodel` 才是几何体的源，按它重算能避免两者再次脱节（曾经出现过东/西碗壁的 UV 被对调、只在 `.geo.json` 里错的情况）。注意 .bbmodel 里内嵌的贴图是快照：用脚本重写 .bbmodel 后再在 Blockbench 里打开，需要 `Texture.all.forEach(t => t.reloadTexture())` 才会读到磁盘上的新贴图。

Blockbench 的 "Export Bedrock Geometry" 写出的是新版 Bedrock 默认版本（当前 `1.21.110`），GeckoLib 4 的 `FormatVersion` 只认到 `1.21.20`——放进去游戏里整个方块不渲染。导出后跑一次 `node 3dmodels/blocks/tools/normalize_geo.js` 就会把 `format_version` 改回 `1.12.0` 并统一排版，幂等，可以每次都跑。

`previews/` 下的预览用同一机位（正交、`[42,26,-42]` 看向 `[0,4,0]`；祭坛是 `[40,26,-40]` 看向 `[0,8,0]`；传送门框架是 `[32,20,-32]` 看向 `[0,8,0]`）在 Blockbench 里截图，方便直接比对状态。

## 验证

`EndgameGeoResourcesTest` 使用 GeckoLib 自身的解析器和模型烘焙器验证全部 10 个状态，检查纹理尺寸、模型范围、最短轴、UV、状态几何一致性、发光遮罩和 7 个物品入口。其中 `altarFacesMapOneTexelPerUnit` 专门盯祭坛三个状态：每个面的 UV 尺寸必须等于该面的模型尺寸，一旦对不上就说明贴图被拉伸。

模型范围一律按"未旋转的包围盒"检查并限制在 ±8 内，只有转化台放宽到 ±16（符文阵刻意的悬挑）。旋转立方体的真实外沿由 `gen_conversion_platform.js` 的自检按绕 pivot 旋转后的八个角单独核对。

骨与立方体在 geo 里是无序集合，Blockbench 重新导出时常会重排，所以 `stateChangesPreserveGeometryAndUv` 先把它们按内容排序再比，只关心几何本身相不相同。

**当前状态**：基座、祭坛三态、转化台、传送门框架全部通过。导线两态只剩一项会红——两边都有个 `[1.5, 0.2, 1.5]` 的立方体，最短轴 0.2 违反了"最短轴不小于 0.5"的约定。补到 0.5 即可恢复绿色。

尚未进行游戏内场景、手持或旧存档迁移的实机验证；转化台的半砖碰撞和符文阵悬挑也只在模型与测试层面确认过。

参考：[GeckoLib 4 方块接入](https://github.com/bernie-g/geckolib/wiki/Geckolib-Blocks-%28Geckolib4%29)、[GeckoLib 4 渲染层](https://github.com/bernie-g/geckolib/wiki/Render-Layers-%28Geckolib4%29)。API 已按项目缓存的 4.8.4 源码核对。
