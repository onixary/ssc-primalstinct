# 新的本能页面

对应 Heptabase 白板“官方附属：SSC-Primalstinct”的“新的本能页面”卡片（`61f32881-4b82-47ba-9a5c-736185855bab`）。

受管理形态在幻形者之书 V2 第二页的本能栏看到统一提示和“查看本能”按钮。原本能正文、“+”详情入口与附属 Logo 不再显示。普通形态继续使用 SSC 页面。

新页仅显示当前形态、当前本能阶段的两段说明，不提供阶段翻阅或独立按键绑定。阶段/形态变化后自动刷新；左右栏独立换行、滚动，支持滚轮、拖动滚动条和聚焦后的方向键/Page Up/Page Down/Home/End。右上角关闭按钮和 Esc 均返回原书页。

接入沿用 `CodexInstinctColumnHooks` 文本接口，按钮通过 Fabric `ScreenEvents.AFTER_INIT` 与 `Screens.getButtons` 添加。入口适配依赖 SSC 1.10.0 第二页的本能详情按钮位置 `(308, 13)`，支持 SSC 的两种书页缩放；SSC 改布局后需复核 `SSCClientAdapter` 中的位置。

本地化文件：`assets/ssc-primalstinct/lang/zh_cn.json`、`en_us.json`。

每个形态、阶段、栏位可分别编辑：

```text
codex.ssc-primalstinct.page.form.<namespace>.<path>.level.<N>.instincts
codex.ssc-primalstinct.page.form.<namespace>.<path>.level.<N>.abilities
```

例如豹猫第三阶段的能力说明为 `codex.ssc-primalstinct.page.form.shape-shifter-curse.ocelot_3.level.3.abilities`。子形态使用自身完整 ID，互不覆盖。文本中 `\n` 表示换行。现有四个形态已分别配置 L1–L5 的两栏词条；能力文案依据当前内置数据包，调整 Power 或服务器自定义数据包后应同步调整资源包文案。

缺失专属词条时，先尝试 `codex.ssc-primalstinct.page.level.<N>.<section>`，再使用通用说明。等级数量来自服务端快照，超出默认五级不会直接显示缺失的翻译键。

背景资源路径为 `assets/ssc-primalstinct/textures/gui/primalstinct_page.png`，建议画布比例 5:4，例如 1000×800；整张贴图映射到主体区域，Logo 可直接绘在其中。未放入贴图时使用浅色底板。主体通常占 GUI 宽度的一半，高度受窗口限制；左栏约占主体高度七成，右栏接近全高，左下显示当前阶段。全屏半透明遮罩覆盖下层书页与场景。

兼容性修复：新页纳入 `PerceptionClientState.screenChance()` 的书页分类，使用 Power 同步的 `codex_chance` 和既有稳定乱码字形逻辑；每帧按最新状态生效。书页渲染后先提交绘制，再将遮罩及新页整体放到前景 Z=400，避免底层书页标题/文字阴影穿透。

验证：`gradlew.bat build --offline` 通过（编译、现有单元测试、remap 打包）。实机由用户验证：当前阶段更新、乱码概率变化与解除、底层标题遮挡、双栏滚动、关闭返回及两种 SSC 书页缩放。

## 150% layout and background asset

The page now targets 150% of the original layout (75% of GUI width), with proportional panel spacing and close-button bounds. Short windows uniformly reduce the layout to keep it on screen. Text remains at the native GUI font size for readability. The background is now the bundled 1000x800 PNG at `assets/ssc-primalstinct/textures/gui/primalstinct_page.png`; replace this file to supply the final artwork. The initial asset preserves the previous plain light panel and border.
