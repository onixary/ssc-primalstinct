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
 * 显示规则（隐藏面）：
 * - 无玩家 / hudHidden / 旁观 / 创造（无状态栏）→ 不绘制；
 * - 无快照（服务端未运行本附属）或 managed=false（pending、非名单形态）→ 不绘制；
 * - 名单内形态一律显示——不读 SSC 的 NoInstinct flag（该 flag 仍影响 SSC 其他系统，仅不再决定本条显隐）。
 *
 * 数值规则（权威面）：
 * - 条长 = 快照 value + rate × 经过时间外推，钳制 [0, maxValue]（ClientPrimalstinctState 平滑纠正）；
 * - 跨阶提示、满值锁定覆盖使用快照的 level/locked（服务端状态），客户端预测不得提前解除锁定表现；
 * - 速率分档外框：保留旧 SSC 系统——rate 相对 baseRate 的超出量决定整条（空槽+填充）的分档贴图行，
 *   基础自然增长保持平稳外观，Power 加速增长逐档警示（微增/I/II/III），负向下降为青蓝档。
 *
 * 提示规则：跨阶提示走原版 actionbar 位置（InGameHud.setOverlayMessage，屏幕下方居中、
 * 与其它模组通用的标签表现）：升阶 L1–L4 各自文案、升至满值（锁定）专用文案、降阶共用一条。
 *
 * 位置规则：默认对齐旧 SSC 本能条（中下锚 +100,-9），锚点/偏移见 PrimalstinctClientConfig；
 * 法力条顶替旧本能条位（OverrideInstinctBar）时整体上移 14px 避让，两类资源同屏可见。
 * dev 读数固定屏幕左上角（卡03 占位 HUD 的原位），不随条位置移动、不出屏。
 */
@Environment(EnvType.CLIENT)
public final class PrimalInstinctHud {

    /** 与 tools/gen_primal_bar_texture.py 成对维护：七行 5px，每行左半空槽 + 右半填充。 */
    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 5;
    private static final int TEXTURE_WIDTH = 160;
    private static final int TEXTURE_HEIGHT = 35;
    private static final int V_DECREASE = 0;
    private static final int V_IDLE = 5;
    private static final int V_SLIGHT_INCREASE = 10;
    private static final int V_INCREASE_1 = 15;
    private static final int V_INCREASE_2 = 20;
    private static final int V_INCREASE_3 = 25;
    private static final int V_LOCK = 30;

    /** 分档阈值（点/秒，相对 baseRate 的超出量）——对齐旧 SSC updateBarTextures 的分档节奏。 */
    private static final float TIER_SLIGHT = 0.005f;
    private static final float TIER_INCREASE_1 = 0.01f;
    private static final float TIER_INCREASE_2 = 0.1f;
    /** 速率死区：|rate| 低于此值按 0 处理（快照浮动噪声）。 */
    private static final float RATE_EPSILON = 0.0005f;

    /** 法力条（OverrideInstinctBar 实现）在场时的自动上移量：条高 5 + 数字文本 9。 */
    private static final int MANA_AVOID_SHIFT_Y = -14;

    /** dev 读数固定左上角坐标（卡03 占位 HUD 原位）。 */
    private static final int DEV_READOUT_X = 4;
    private static final int DEV_READOUT_Y = 4;

    private static final Identifier TEXTURE =
            Identifier.of(SSCPrimalstinct.MOD_ID, "textures/gui/primal_instinct_bar.png");

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
        renderStatusText(context, client, snapshot, value, x, y, config);
        renderDevReadout(context, client, snapshot, value);
    }

    private static void renderBar(DrawContext context, PrimalstinctStateS2C snapshot, float value, int x, int y) {
        int fillWidth = (int) Math.ceil(BAR_WIDTH * (value / snapshot.maxValue()));
        fillWidth = Math.max(0, Math.min(BAR_WIDTH, fillWidth));
        int row = rateRow(snapshot);
        RenderSystem.enableBlend();
        // 原版式左→右填充（左为 0、右为满值）：填充取本行右半自 u=80 起，空槽取左半右侧剩余段
        if (fillWidth > 0) {
            context.drawTexture(TEXTURE, x, y, BAR_WIDTH, row, fillWidth, BAR_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        if (fillWidth < BAR_WIDTH) {
            context.drawTexture(TEXTURE, x + fillWidth, y, fillWidth, row, BAR_WIDTH - fillWidth, BAR_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        // L0–L5 分级刻度：按快照同步的阈值表程序化绘制（适配任意阈值；左 0 右满 → 阈值像素自左端起算）
        drawLevelTicks(context, snapshot, x, y);
        // 满值锁定覆盖：以服务端 locked 状态为准，不据客户端预测值推断
        if (snapshot.locked()) {
            context.drawTexture(TEXTURE, x, y, 0, V_LOCK, BAR_WIDTH, BAR_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        RenderSystem.disableBlend();
    }

    /**
     * 速率分档行选择（保留旧 SSC updateBarTextures 语义）：
     * 负向 → 下降；正向按超出 baseRate 的量分 微增/I/II/III；基础自然增长（≈baseRate）保持平稳档。
     */
    private static int rateRow(PrimalstinctStateS2C snapshot) {
        float rate = snapshot.rate();
        float base = snapshot.baseRate();
        if (rate < -RATE_EPSILON) {
            return V_DECREASE;
        }
        if (rate > base + TIER_INCREASE_2) {
            return V_INCREASE_3;
        }
        if (rate > base + TIER_INCREASE_1) {
            return V_INCREASE_2;
        }
        if (rate > base + TIER_SLIGHT) {
            return V_INCREASE_1;
        }
        if (rate > base) {
            return V_SLIGHT_INCREASE;
        }
        return V_IDLE;
    }

    /** 每个阈值（不含 0 与满值右端）在条上画 1px 半透明刻线；左为 0、右为满值。 */
    private static void drawLevelTicks(DrawContext context, PrimalstinctStateS2C snapshot, int x, int y) {
        float max = snapshot.maxValue();
        for (float threshold : snapshot.thresholds()) {
            if (threshold <= 0.0f || threshold >= max) {
                continue;
            }
            int offset = Math.round(BAR_WIDTH * (threshold / max));
            int tickX = Math.max(x + 1, Math.min(x + BAR_WIDTH - 2, x + offset));
            context.fill(tickX, y, tickX + 1, y + BAR_HEIGHT, 0xD9FFFFFF);
        }
    }

    private static void renderStatusText(DrawContext context, MinecraftClient client,
                                         PrimalstinctStateS2C snapshot, float value,
                                         int x, int y, PrimalstinctClientConfig config) {
        if (!config.showStatusText) {
            return;
        }
        Text status = null;
        int color = 0xFFFFFF;
        if (snapshot.locked()) {
            status = Text.translatable("hud.ssc-primalstinct.locked");
            color = 0xFFD75E;  // 金
        } else if (Math.abs(snapshot.rate()) <= RATE_EPSILON && value > 0.0f) {
            status = Text.translatable("hud.ssc-primalstinct.paused");
            color = 0xA9B7C6;  // 灰蓝
        }
        if (status != null) {
            context.drawText(client.textRenderer, status, x + BAR_WIDTH + 4, y - 2, color, false);
        }
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
            client.inGameHud.setOverlayMessage(
                    levelChangeMessage(newLevel, levelUp, snapshot.thresholds().length), false);
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
