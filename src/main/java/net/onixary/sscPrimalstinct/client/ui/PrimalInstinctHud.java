package net.onixary.sscPrimalstinct.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCClientAdapter;
import net.onixary.sscPrimalstinct.client.PrimalstinctClientConfig;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;
import org.jetbrains.annotations.Nullable;

/**
 * 卡15：正式本能 HUD（替换卡03 的 DebugHudPlaceholder）。
 *
 * 条与外框的绘制逻辑完全复刻 SSC InstinctBarRenderer（含速率分档行选择、两段取图、
 * 满值锁定行），贴图使用复制到本命名空间的 instinct_bar.png（美术可自行修改区分）；
 * 数值与锁定来源换成新系统：条长按快照 value + rate 外推并平滑纠正，锁定读快照 locked。
 * 档位分界叠加线存放在同一贴图未使用的右下角（u=80..160, v=35..40），未锁定时叠加渲染。
 *
 * 显示规则（隐藏面）：
 * - 无玩家 / hudHidden / 旁观 / 创造（无状态栏）→ 不绘制；
 * - 无快照（服务端未运行本附属）或 managed=false（pending、非名单形态）→ 不绘制；
 * - 名单内形态一律显示——不读 SSC 的 NoInstinct flag（该 flag 仍影响 SSC 其他系统，仅不再决定本条显隐）。
 *
 * 权威与预测分离：跨阶提示、满值锁定样式只读快照 level/locked，客户端预测不得提前解除表现。
 * 提示走原版 actionbar 位置（InGameHud.setOverlayMessage）：升阶 L1–L4 各自文案、
 * 升至满值锁定专用文案、降阶共用一条；方向由连续快照 level 对比得出。
 *
 * 位置规则：默认对齐旧 SSC 本能条（中下锚 +100,-9），锚点/偏移见 PrimalstinctClientConfig；
 * 法力条顶替旧本能条位（OverrideInstinctBar）时整体上移 14px 避让，两类资源同屏可见。
 * dev 读数固定屏幕左上角（卡03 占位 HUD 的原位），不随条位置移动、不出屏。
 */
@Environment(EnvType.CLIENT)
public final class PrimalInstinctHud {

    /** SSC instinct_bar.png 布局（160x40，8 行 5px；行 10 未使用）：0 下降 / 5 平稳 / 15 微增 / 20 增长I / 25 增长II / 30 增长III / 35 满值锁定。 */
    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 5;
    private static final int TEXTURE_WIDTH = 160;
    private static final int TEXTURE_HEIGHT = 40;
    private static final int V_DECREASE = 0;
    private static final int V_IDLE = 5;
    private static final int V_SLIGHT_INCREASE = 15;
    private static final int V_INCREASE_1 = 20;
    private static final int V_INCREASE_2 = 25;
    private static final int V_INCREASE_3 = 30;
    private static final int V_LOCK = 35;

    /** 档位分界叠加线存放在贴图未使用的右下角（u=80..160, v=35..40，即锁定行右半）。 */
    private static final int U_LEVEL_MARKS = 80;
    private static final int V_LEVEL_MARKS = 35;

    /** SSC updateBarTextures 的分档阈值（点/秒，相对 baseRate 的超出量）。 */
    private static final float TIER_SLIGHT = 0.0f;
    private static final float TIER_INCREASE_1 = 0.005f;
    private static final float TIER_INCREASE_2 = 0.01f;
    private static final float TIER_INCREASE_3 = 0.1f;

    /** 法力条（OverrideInstinctBar 实现）在场时的自动上移量：条高 5 + 数字文本 9。 */
    private static final int MANA_AVOID_SHIFT_Y = -14;

    /** dev 读数固定左上角坐标（卡03 占位 HUD 原位）。 */
    private static final int DEV_READOUT_X = 4;
    private static final int DEV_READOUT_Y = 4;

    private static final Identifier BAR_TEXTURE =
            Identifier.of(SSCPrimalstinct.MOD_ID, "textures/gui/instinct_bar.png");

    // 以下状态仅渲染线程访问
    private static @Nullable PrimalstinctStateS2C lastSnapshot;
    private static int lastLevel = -1;

    private PrimalInstinctHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(PrimalInstinctHud::render);
        SSCPrimalstinct.LOGGER.debug("Primalstinct instinct HUD registered");
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.client.network.ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || player.isSpectator()) {
            return;
        }
        // 与旧 SSC 本能条一致：创造模式（无状态栏）不绘制
        if (client.interactionManager == null || !client.interactionManager.hasStatusBars()) {
            return;
        }
        PrimalstinctStateS2C snapshot = ClientPrimalstinctState.snapshot();
        if (snapshot == null || !snapshot.managed()) {
            return;
        }
        trackLevelChange(client, snapshot);

        float value = ClientPrimalstinctState.displayValue(player, tickDelta);
        if (Float.isNaN(value)) {
            return;
        }
        PrimalstinctClientConfig config = PrimalstinctClientConfig.get();
        int[] position = anchoredPosition(client, config.barPosType);
        int x = position[0] + config.barOffsetX;
        int y = position[1] + config.barOffsetY;
        if (SSCClientAdapter.manaOverridesInstinctBar()) {
            y += MANA_AVOID_SHIFT_Y;
        }

        renderBar(context, snapshot, value, x, y);
        renderDevReadout(context, client, snapshot, value);
    }

    /** SSC renderInstinctBar 逐行复刻：空槽左段 + 填充右段（右锚，自右向左增长）+ 锁定行 + 分界叠加线。 */
    private static void renderBar(DrawContext context, PrimalstinctStateS2C snapshot, float value, int x, int y) {
        int fillWidth = Math.max(0, Math.min(BAR_WIDTH,
                (int) Math.ceil(BAR_WIDTH * (value / snapshot.maxValue()))));
        int row = rateRow(snapshot);
        RenderSystem.enableBlend();
        if (fillWidth < BAR_WIDTH) {
            context.drawTexture(BAR_TEXTURE, x, y, 0, row, BAR_WIDTH - fillWidth, BAR_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        if (fillWidth > 0) {
            context.drawTexture(BAR_TEXTURE, x + BAR_WIDTH - fillWidth, y, TEXTURE_WIDTH - fillWidth, row,
                    fillWidth, BAR_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        // 满值锁定行：SSC 的 y=35 覆盖行；触发条件换为新系统快照 locked（SSC 旧判定来源已随卡04 失效）
        boolean locked = snapshot.locked();
        if (locked) {
            context.drawTexture(BAR_TEXTURE, x, y, 0, V_LOCK, BAR_WIDTH, BAR_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        // 档位分界叠加线：取贴图右下角（锁定行右半），叠加在最上层；锁定时不渲染（锁定行整体覆盖即可读）
        if (!locked) {
            context.drawTexture(BAR_TEXTURE, x, y, U_LEVEL_MARKS, V_LEVEL_MARKS,
                    BAR_WIDTH, BAR_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        RenderSystem.disableBlend();
    }

    /**
     * SSC updateBarTextures 逐行复刻：rate 相对 baseRate 的超出量选行（基础自然增长=平稳行）。
     * 一次性事件（add_primalstinct / 物品 / 命令）不改变持续速率：脉冲方向由服务端随快照下发，
     * 正脉冲将行至少抬到增长I、负脉冲强制下降行，窗口 40 tick（2026-09-16）。
     */
    private static int rateRow(PrimalstinctStateS2C snapshot) {
        float rate = snapshot.rate();
        float base = snapshot.baseRate();
        if (rate > base + TIER_INCREASE_3) {
            return V_INCREASE_3;
        }
        if (rate > base + TIER_INCREASE_2) {
            return V_INCREASE_2;
        }
        int row = V_IDLE;
        if (rate > base + TIER_INCREASE_1) {
            row = V_INCREASE_1;
        } else if (rate > base + TIER_SLIGHT) {
            row = V_SLIGHT_INCREASE;
        } else if (rate < 0.0f) {
            row = V_DECREASE;
        }
        int pulse = snapshot.pulseDirection();
        if (pulse > 0) {
            return Math.max(row, V_INCREASE_1);  // 持续高增速时保留更高档
        }
        if (pulse < 0) {
            return V_DECREASE;
        }
        return row;
    }

    /**
     * 快照换代时检测跨阶 → 原版 actionbar 位置提示（屏幕下方居中）。
     * 方向由连续快照的 level 对比得出（服务端每次跨阶即时 syncNow，权威且不受客户端平滑影响；
     * 平滑速度差在速率趋 0 或纠正收敛阶段会误判方向）。升阶 L1–L4 各自文案；
     * 升至满值（最高级）显示锁定专用文案；降阶无论落点共用一条文案。
     */
    private static void trackLevelChange(MinecraftClient client, PrimalstinctStateS2C snapshot) {
        if (snapshot == lastSnapshot) {
            return;
        }
        int newLevel = snapshot.level();
        if (lastLevel >= 0 && newLevel != lastLevel) {
            boolean levelUp = newLevel > lastLevel;
            // 满值锁定的提示不在此即时弹出：服务端在锁定演出（SSC 变形屏幕效果）结束后定时发送
            if (!(levelUp && newLevel >= snapshot.thresholds().length)) {
                client.inGameHud.setOverlayMessage(
                        levelChangeMessage(newLevel, levelUp, snapshot.thresholds().length), false);
            }
        }
        lastSnapshot = snapshot;
        lastLevel = newLevel;
    }

    private static Text levelChangeMessage(int newLevel, boolean levelUp, int maxLevel) {
        if (!levelUp) {
            return Text.translatable("hud.ssc-primalstinct.level_down");
        }
        if (newLevel >= maxLevel) {
            return Text.translatable("hud.ssc-primalstinct.level_up.locked");
        }
        return switch (newLevel) {
            case 1 -> Text.translatable("hud.ssc-primalstinct.level_up.1");
            case 2 -> Text.translatable("hud.ssc-primalstinct.level_up.2");
            case 3 -> Text.translatable("hud.ssc-primalstinct.level_up.3");
            case 4 -> Text.translatable("hud.ssc-primalstinct.level_up.4");
            // 自定义等级表超出预设文案的等级：升阶通用文案
            default -> Text.translatable("hud.ssc-primalstinct.level_up", newLevel);
        };
    }

    /** 开发读数：固定左上角（卡03 占位 HUD 原位），验收边界值时核对精确数值与外推。 */
    private static void renderDevReadout(DrawContext context, MinecraftClient client,
                                         PrimalstinctStateS2C snapshot, float value) {
        if (!SSCPrimalstinct.isDevelopmentEnvironment()) {
            return;
        }
        String line = String.format("%.2f/%.0f L%d rate%+.5f%s rev%d", value, snapshot.maxValue(),
                snapshot.level(), snapshot.rate(), snapshot.locked() ? " [LOCKED]" : "", snapshot.revision());
        context.drawTextWithShadow(client.textRenderer, line, DEV_READOUT_X, DEV_READOUT_Y, 0xFFFFFFAA);
        // 第二行：过热计量条（t=检测目标数，[BUFF]=Buff 期间冻结）
        if (net.onixary.sscPrimalstinct.client.network.PerceptionClientState.heatPresent()) {
            String heat = String.format("heat %.1f/100 t%d%s",
                    net.onixary.sscPrimalstinct.client.network.PerceptionClientState.heatMeter(),
                    net.onixary.sscPrimalstinct.client.network.PerceptionClientState.heatTargets(),
                    net.onixary.sscPrimalstinct.client.network.PerceptionClientState.heatFrozen() ? " [BUFF]" : "");
            context.drawTextWithShadow(client.textRenderer, heat, DEV_READOUT_X, DEV_READOUT_Y + 10, 0xFFFFFFAA);
        }
    }

    /** 九宫格锚点（与 SSC UIPositionUtils 同布局；不复用其客户端类，避免 UI 包直接依赖 SSC）。 */
    private static int[] anchoredPosition(MinecraftClient client, int posType) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int anchorX;
        int anchorY;
        switch (posType) {
            case 1 -> { anchorX = 0; anchorY = 0; }
            case 2 -> { anchorX = width / 2; anchorY = 0; }
            case 3 -> { anchorX = width; anchorY = 0; }
            case 4 -> { anchorX = 0; anchorY = height / 2; }
            case 5 -> { anchorX = width / 2; anchorY = height / 2; }
            case 6 -> { anchorX = width; anchorY = height / 2; }
            case 7 -> { anchorX = 0; anchorY = height; }
            case 9 -> { anchorX = width; anchorY = height; }
            default -> { anchorX = width / 2; anchorY = height; }  // 8 与非法值：中下
        }
        return new int[]{anchorX, anchorY};
    }

    /** 断线清理跨阶追踪（重进后由新快照重建）。 */
    public static void clearHint() {
        lastSnapshot = null;
        lastLevel = -1;
    }
}
