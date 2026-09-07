# 游荡 AI 排查记录

参考：本地 `naturalis-1.20.1-forge-1.7.1.jar` 的 `InstinctEvents`、`VanillaMobWanderDriver`，以及项目使用的 MC 1.20.1 Yarn build.10 字节码。

## 当前移动链路：坠落卡顿修复

实测日志存在大量 `moved wrongly!`。旧实现每 tick 在服务端 `player.move`，再通过 `velocityModified` 下发包含代理重力的完整速度；客户端自身也执行移动和重力，并回报位置。这使服务端额外位移与客户端预测冲突，坠落速度越大偏差越明显。下文第一轮速度标记修复为历史记录，现已替换该移动链路。

当前服务端只运行代理 AI，下发 `WanderMotionS2C` 水平速度与可选的一次性起跳指令，不直接移动玩家、不下发连续 Y 速度。客户端在原版移动前更新水平速度，保留原有 Y；起跳只在落地时消费一次，重力、下落、碰撞和位置回报由原版玩家移动处理。`LivingEntityWanderJumpMixin` 在代理 jump 尾部捕获重力处理前的起跳速度，保留高度倍率。空中迟到的跳跃指令直接丢弃，不储存到落地再触发。

移动输入立即暂停客户端接管，直到服务端确认释放；断线清理状态，连续 20 个客户端 tick 未收到指令时停止应用 AI。鼠标不参与退出。新增 4 项回归测试覆盖持续 AI 包下的自由落体、包合并、一次性起跳、迟到/释放指令；共 26 项测试及 remapJar 通过。未实机验证，建议复测高处坠落、边缘走落、2 倍跳跃落地、途中按键退出，并观察 `moved wrongly!` 是否仍出现。

## 直接问题

- AFK 检测把 `getVelocity().lengthSquared() > 1e-4` 当成操作。落地后物理仍可留下约 `-0.0784` 的 Y 速度，平方约 `0.00615`，导致静止玩家持续刷新 lastActiveTick。现在只判断水平速度；真实按键另由 C2S 检测。
- `velocityDirty` 触发 EntityTrackerEntry 向追踪者广播，不包含玩家自己。现在使用 `velocityModified`，走包含自身的 sendSyncPacket 通道，与 Naturalis 的运动同步标记一致。
- `ClientPlayerEntity.tick` 仅在 hasVehicle 分支发送 PlayerInputC2SPacket。原 updateInput 注入不能检测普通步行，旧客户端也仅在接管后上报。现在空闲时也上报，顶墙走路、跳跃、潜行、攻击和使用均可阻止 AFK。鼠标转向不重置 AFK，也不释放接管。

## 代理与清理

- 不加入世界的实体不会收到 ServerWorld.tickEntity 对 age 的递增。现在自行递增并每 tick 调用 AI；补齐 age 后不能固定隔 tick 更新，因为 tickNewAi 按 serverTicks + entityId 的奇偶决定是否启动 Goal。
- 陆地代理保留重力并开启 AI、持久化、禁止捡物。无重力会影响下落及落地状态，不符合参考实现的陆地物理配置。
- 创建状态时从当前 tick 开始计时；死亡、旁观、骑乘、飞行时不接管；跨世界重建代理；退出托管时清理代理；清理玩家时移除旧输入时间戳。

## 第二轮修复：接管后长期原地停留

- `MobEntity.tickNewAi` 每次增加 despawnCounter；`WanderAroundGoal.canStart` 在该计数达到 100 时拒绝启动。正常世界在实体 tick 前调用 checkDespawn，为持久化/附近生物重置计数，但未入世界的代理没有这一步。此前只调用 setPersistent 并不能重置计数。现在每 tick 在 AI 更新前显式归零，避免接管约 5 秒后永久停止申请游荡目标。这是字节码确认的阻断路径，未做实机复现。
- 豹猫的 TemptGoal 优先级高于 WanderAroundFarGoal。玩家持鱼时，重合的代理会选中玩家，而 TemptGoal.tick 在距离平方小于 6.25 时停止导航。现在仅在创建游荡代理时移除 TemptGoal，避免持食物使代理一直停在玩家身边。普通世界生物不变；代理也不再被其他玩家的食物吸引。
- 按用户要求完全删除 WanderLookS2C 和镜头写入，客户端改为仅上报按键的 WanderInputClientState。玩家可自由转动镜头，不重置等待计时、不退出游荡。代理自身仍保留 AI 朝向以执行移动。

## 验证

跳跃调整：新增 `jump_height_multiplier`，默认 1，豹猫设为 2。仅代理普通跳跃的起跳速度由原版陆地重力（0.08）与竖直阻力（0.98）换算，让理想无阻挡跳跃高度翻倍；不把整个 Y 速度每 tick 翻倍。代理产生的运动仍经已有链路传给玩家，实际高度受碰撞、同步和其他模组物理影响，需实机确认。普通手动跳跃、下落和鼠标控制不变。新增 3 项数值回归测试，`test remapJar --offline` 通过。

第三轮手感调整：代理仍每服务端 tick 更新一次。新增 Power 参数 `wander_chance`（默认 120，越小越频繁，最小 1），通过现有 WanderAroundGoal.setChance 调整随机等待；新增 `speed_multiplier`（默认 1），在代理创建时缩放移动速度属性，不缩放重力/跳跃速度。豹猫配置采用 20 和 1.5，游荡抽取概率约为此前 6 倍，移动属性为此前 1.5 倍；具体起步仍取决于 Goal 调度、优先级和可达目标。AFK 等待仍为 200 tick。Power 实例变化时重建代理以应用新参数。

第三轮 `gradlew.bat test remapJar --offline` 通过，手感待用户实机确认。

第二轮 `gradlew.bat clean test remapJar --offline` 通过；未启动游戏。现有单元测试不覆盖 Minecraft 运行期 AI/网络交互。

建议实机检查：豹猫三阶形态、本能等级至少 3 且未锁定，停止按键 200 tick 后进入接管。原生游荡 Goal 存在随机等待，接管日志出现后不保证立即迈步。在开阔地持续观察超过一分钟，检查多次停走后仍能再次游荡；空手和主/副手持生鱼分别检查。转动鼠标应不受牵引且不退出；WASD/跳跃/潜行/攻击/使用应退出；持续顶墙按键不接管，切换形态结束游荡。
