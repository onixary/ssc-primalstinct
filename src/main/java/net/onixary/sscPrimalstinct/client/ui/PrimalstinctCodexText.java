package net.onixary.sscPrimalstinct.client.ui;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;

/** Resource-pack text, resolved independently for each form, stage and column. */
public final class PrimalstinctCodexText {
    public static final String PREFIX = "codex.ssc-primalstinct.page.";
    private PrimalstinctCodexText() {}

    public static Text section(Identifier form, int level, String section) {
        String key = PREFIX + "form." + form.getNamespace() + "." + form.getPath()
                + ".level." + level + "." + section;
        if (Language.getInstance().hasTranslation(key)) return Text.translatable(key);
        String fallback = PREFIX + "level." + level + "." + section;
        if (Language.getInstance().hasTranslation(fallback)) return Text.translatable(fallback);
        // 无专属文案：显示空白，不再触发通用兜底句（2026-09-14 用户决策）
        return Text.empty();
    }

    public static Text formName(Identifier form) {
        String key = "ssc-primalstinct.form." + form.getNamespace() + "." + form.getPath() + ".name";
        return Language.getInstance().hasTranslation(key) ? Text.translatable(key) : Text.literal(form.toString());
    }

    public static int maxLevel() {
        var snapshot = ClientPrimalstinctState.snapshot();
        return snapshot == null || snapshot.thresholds().length == 0 ? 5 : snapshot.thresholds().length;
    }

    public static int currentLevel() {
        var snapshot = ClientPrimalstinctState.snapshot();
        return snapshot == null ? 1 : Math.max(1, Math.min(maxLevel(), snapshot.level()));
    }
}
