# SSC 兼容性检查（2026-10-24）

基线：相邻 `shape-shifter-curse-fabric` 仓库提交 `86d75e58`，版本仍为 1.10.0。重新执行主项目 `gradlew remapJar --offline` 后，以生成的 jar 编译和运行附属。原有 build/libs 基线是 10 月 7 日产物，单看版本号无法判断是否包含当前接口。

## 已修复

| 问题 | 影响 | 修复 |
| --- | --- | --- |
| `FormUtils._loadForm` 新增 `shouldReload` 参数 | 两处旧调用无法对当前 SSC 编译 | 同形态重建传入 `true`，确保被屏蔽的 origin 基线能够重建；首次选择调用 `setForm`，同时执行变形生命周期和形态历史更新 |
| L2–L5 挖掘能力使用不存在的 `barehand_digging` 条件 | 四个 power 加载失败，继而整个附属形态名单引用校验失败 | 使用主模组当前的 `can_barehand_digging` |
| SSC `reApplyPower` 不触发 `FORM_CHANGE_END` | 技能重置等独立重建路径重新挂回原版突袭/闪避，绕过附属屏蔽 | 在该方法返回后排入已有的服务端能力结算队列 |
| 原始豹猫的 Perk 隔离（后续需求） | 不应共享普通豹猫的技能树 | 使用空技能树；阻止调谐信标和 Perk 界面入口并显示中英文提示，普通豹猫的解锁进度保留 |

同步更新了 `run/client/mods` 和 `run/server/mods` 中的 SSC jar。以后主项目源码更新时仍需先重新构建并同步；附属的 `ssc_version=1.10.0` 本身不能锁定具体提交。

## 检查与验证

- 对照了 SSC 本能读写及 Mixin 目标、形态注册/重建/历史、Origins 能力来源、技能树、变形回调、图鉴扩展、预览动画及模型入口。
- 原始豹猫复制的主模组基础 power 列表与当前豹猫基础列表一致；突袭和闪避替代配置仍沿用当前 SSC 工厂，只额外增加附属本能动作。
- `gradlew build --offline` 通过：47 项单元测试通过，产出 `build/libs/ssc-primalstinct-0.1.0.jar`。
- 服务器回归框架：`gradlew runServer --offline --init-script tools/endgame-regression.init.gradle`。覆盖服务端 Mixin、能力数据、形态名单、重建排队、屏蔽恢复、形态历史，以及原始豹猫 Perk 隔离和信标拒绝。
- 服务端成功加载两个附属形态 profile，名单 revision 1 生效。修复后无附属 power 解析错误或名单引用错误。
- `git diff --check` 通过。保留任务开始时已有的终局机制、库存和语言文件修改；既有未跟踪测试框架补充了兼容性检查并更新旧调用。

## 验证边界及主模组日志

尚未进行客户端画面及多人联机操作验收；客户端代码已编译，图鉴/模型接口已对照源码，不能据此宣称所有视觉表现已验证。

当前 SSC 在测试服务器上仍报告两个自身配方 `alter/moondust_copy`、`alter/moondust_copy_high_speed` 的 `alter_shapeless` 类型未注册，以及服务端无法加载客户端变形动画注册类的警告。这些未阻止服务器和上述回归通过，本次没有修改主模组源码。附属实体的 data fixer 警告也仍存在。
