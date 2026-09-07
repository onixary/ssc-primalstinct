package net.onixary.sscPrimalstinct.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;

/**
 * 卡16：图鉴第二页 INSTINCTS 列的受上下文限制文本替换（仅管理形态生效，
 * 由 BookOfShapeShifterScreenV2_P2Mixin 重定向 CodexData 请求时调用）。
 * 主列滚动文本与"+"详情按钮读同一构建源（mixin 对两处调用返回相同内容），
 * 保证主列与详情一致；数值/来源信息不写入静态 CodexData，其他图鉴页面不受影响。
 */
@Environment(EnvType.CLIENT)
public final class PrimalstinctCodexText {

    private static final int PRESET_LEVEL_KEYS = 6;  // level.0 .. level.5（L0–L5）

    private PrimalstinctCodexText() {
    }

    /** 固定说明行（替换 CodexData.getDescText 的 INSTINCTS 位置）。 */
    public static Text instinctsDesc(PlayerEntity player) {
        return Text.translatable("codex.ssc-primalstinct.instincts.desc");
    }

    /** 逐级说明（替换 CodexData.getContentText 的 INSTINCTS 位置）：L0–Lmax 每级一条本地化词条。 */
    public static Text instinctsContent(PlayerEntity player) {
        int maxLevel = currentMaxLevel();
        MutableText text = Text.empty();
        for (int level = 0; level <= maxLevel; level++) {
            if (level > 0) {
                text.append("\n");
            }
            if (level < PRESET_LEVEL_KEYS) {
                text.append(Text.translatable("codex.ssc-primalstinct.instincts.level." + level));
            } else {
                // 自定义等级表超出预设词条：回退通用词条（带等级号）
                text.append(Text.translatable("codex.ssc-primalstinct.instincts.level_generic", level));
            }
        }
        return text;
    }

    /** 当前等级表最高级（快照同步的阈值数即最高级号，L0–LN；无快照时退回默认 L0–L5）。 */
    private static int currentMaxLevel() {
        PrimalstinctStateS2C snapshot = ClientPrimalstinctState.snapshot();
        if (snapshot == null || snapshot.thresholds().length == 0) {
            return 5;
        }
        return snapshot.thresholds().length;
    }
}
