package net.onixary.sscPrimalstinct.endgame;

import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;

/**
 * 眷属实现01：终局玩法规则常量与正反例表。
 * 每项需求只有一个服务端规则入口（EndgameEligibility / TransformationService / 后续 RitualService），
 * 客户端不自行决定资格。建议默认值（供物清单、概率、门框几何等）集中在数据包
 * data/&lt;ns&gt;/endgame/rituals/default.json 与 variants/*.json，正式内容冻结时复核。
 *
 * <h3>正反例表（资格判定，均以服务端实际状态为准）</h3>
 * <table border="1">
 *   <tr><th>场景</th><th>献祭投物</th><th>领取碎片</th><th>转化台变形</th></tr>
 *   <tr><td>名单内形态 + 实际等级=最高级 + 下蹲（转化台）</td><td>允许</td><td>允许</td><td>允许（映射有效且非变体时）</td></tr>
 *   <tr><td>名单内形态 + 等级低于最高级</td><td>拒绝</td><td>拒绝</td><td>拒绝</td></tr>
 *   <tr><td>名单外形态 / 未选择形态</td><td>拒绝</td><td>拒绝</td><td>拒绝</td></tr>
 *   <tr><td>已是原始变体形态</td><td>按变体等级规则判定</td><td>按变体等级规则判定</td><td>无操作（目标已生效）</td></tr>
 *   <tr><td>等级达标但 source→target 映射缺失/无效</td><td>—</td><td>—</td><td>拒绝</td></tr>
 *   <tr><td>已有运行中的转化会话</td><td>—</td><td>—</td><td>拒绝（等当前会话终态）</td></tr>
 *   <tr><td>祭坛已 SPENT（失效）</td><td>—</td><td>拒绝（永不回退）</td><td>—</td></tr>
 * </table>
 *
 * <h3>状态机不变量（眷属实现02）</h3>
 * <ul>
 *   <li>基座 EMPTY→FULFILLED（单向，不因 reload 重抽）；</li>
 *   <li>祭坛 WAITING↔READY→SPENT，SPENT 绝不回退；</li>
 *   <li>门户 CLOSED→OPEN→BROKEN；</li>
 *   <li>转化 IDLE→STARTED→FORM_APPLIED→COMPLETED 或 FAILED；
 *       完成以实际 FormID 生效为准，不以客户端“动画结束”包为准；</li>
 *   <li>奖励同 tick 只结算一次；返回锚点属于玩家个体。</li>
 * </ul>
 */
public final class EndgameRules {

    /** 终局数据包 schema（数据包文件声明 schema_version；不匹配时拒绝加载并保留旧配置）。 */
    public static final int RITUAL_SCHEMA_VERSION = 1;
    public static final int VARIANT_SCHEMA_VERSION = 1;

    /** 每座祭坛的基座数量（固定三座、三路供能，见设计稿“原初祭坛有三个基座”）。 */
    public static final int PEDESTAL_COUNT = 3;

    /**
     * 门框形状（2026-09-10 用户决策，取代眷属实现01的“外框4×4/内孔2×2”建议默认值）：
     * 末地传送门式水平 5×5 环——去四角共 12 个门框方块（缺角不影响判定），内孔 3×3。
     */
    public static final int PORTAL_FRAME_OUTER = 5;
    public static final int PORTAL_FRAME_INNER = 3;

    /** 转化台检测：脚部落在台顶支撑区域才计入（不能把楼上玩家算进来）。 */
    public static final float PLATFORM_DETECT_HEIGHT = 1.0f;
    /** 转化台服务端检测周期（tick）；仅检测，不做资格判定以外的副作用。 */
    public static final int PLATFORM_TICK_INTERVAL = 4;

    /** 化身实体 Interact 触发查询半径（建议值，眷属实现11）。 */
    public static final double AVATAR_INTERACT_RADIUS = 16.0;

    /** 领取镇静碎片的输出数量（一次性馈赠）。 */
    public static final int SEDATIVE_REWARD_COUNT = 1;

    private EndgameRules() {
    }

    /** “原始本能最高级”读取当前等级表 maxLevel，不写死 5（眷属实现01默认值）。 */
    public static int maxInstinctLevel() {
        return PrimalRosterManager.active().levels.maxLevel();
    }

    public static net.minecraft.util.Identifier id(String path) {
        return net.minecraft.util.Identifier.of(SSCPrimalstinct.MOD_ID, path);
    }
}
