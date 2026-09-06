# 卡10：合成与方块交互修复

## 原因

- ScreenHandlerMixin 在没有 InventoryLockRule 时提前返回，导致独立的禁合成 Power 在 L0 等无锁槽状态被跳过。
- onPlayerInteractBlock 的 HEAD 取消发生在原版 forceMainThread 和 updateSequence 之前。客户端已预测的放置/开门没有完成原版确认流程，不能据此认定服务端真的放置成功。
- sendContentUpdates 只发送与服务端缓存不同的库存；拒绝放置时服务端数量未变，所以不能修复客户端预测扣除的堆栈。
- 交互规则缓存仅在 Power 结算时更新，无法准确反映带动态 condition 的 Power；多个限放置标签也不应只取最后一个。

## 修复

- InteractionRestrictions 在两端读取当前 Power，检查所有禁放置标签和独立合成规则。
- 服务端在 ServerPlayerInteractionManager.interactBlock 拒绝操作，保留网络包的主线程转交、sequence 确认和方块校正；拒绝时强制同步库存。
- 客户端在 interactBlock 预测前拒绝相同操作，避免先放下方块/打开门再回滚。
- 合成结果点击检查独立于背包锁槽；共享 updateResult 清空被禁配方结果，Slot.canTakeItems 防双击收集绕过，配方书请求在主线程上检查。
- 盔甲与工具掉落规则未调整。雪狐 L4 profile 显式撤销禁放置 Power 的设计未改。

## 游戏回归（待实测）

1. 雪狐 L0：背包仍无锁槽时，2×2 合成结果为空；普通取出、Shift、数字键、双击收集、配方书均不可获得成品，原料可取回。
2. 默认 include_crafting_table=false 时工作台3×3仍可用；设为true后再次核验工作台禁止。
3. 雪狐 L0：主/副手放普通方块、最后一个方块、双格方块、可替换草地均失败，物品数量和服务器方块状态保持；换槽/放入新物品后不出现先前隐藏堆栈。
4. 木门上下半、活板门、栅栏门均不可打开；没有禁门Power的玩家可正常打开。床及其他未纳入prevent_door的交互不应被空手禁门规则影响。
5. 雪狐L4按现有配置恢复放置；切回L0恢复禁止。附带condition的Power条件变化后无需重新挂载便可响应。
6. 两人服务器分别观察同一区域；在服务端规则改变而客户端同步尚未到达时尝试放置，确认收到sequence和库存校正后消除预测。
7. 复核工具/武器成功使用后掉落和盔甲限制维持现状。

构建和现有单元测试不等于实际联机行为验证；本回归表需要启动更新后的客户端和服务器验证。
