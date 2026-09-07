package net.onixary.sscPrimalstinct.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.custom_ui.CodexInstinctColumnHooks;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;

/**
 * 卡16：SSC 图鉴第二页 INSTINCTS 列的附属侧 Provider。
 * 迁移自原 S7 mixin（对 P2.init/render 的注入）：SSC 1.10.0 新增公开扩展点
 * CodexInstinctColumnHooks（本接口即 SSC 的公开 API，非内部类引用），
 * 页面侧保证正文单次取用、主列与"+"详情同源、列底贴图按列布局绘制。
 * 非管理形态全部返回 null，页面回退原版 CodexData 行为。
 */
@Environment(EnvType.CLIENT)
public final class PrimalstinctCodexColumnProvider implements CodexInstinctColumnHooks.Provider {

    public static final PrimalstinctCodexColumnProvider INSTANCE = new PrimalstinctCodexColumnProvider();

    /** 头图原始尺寸须与 assets 内 PNG 一致（页面按此保持宽高比）。 */
    private static final CodexInstinctColumnHooks.BottomTexture TITLE_TEXTURE =
            new CodexInstinctColumnHooks.BottomTexture(
                    Identifier.of(SSCPrimalstinct.MOD_ID, "textures/gui/primalstinct_title.png"), 1341, 138);

    private PrimalstinctCodexColumnProvider() {
    }

    @Override
    public Text instinctsDesc(PlayerEntity player) {
        return PrimalstinctLifecycle.isManaged(player) ? PrimalstinctCodexText.instinctsDesc(player) : null;
    }

    @Override
    public Text instinctsContent(PlayerEntity player) {
        return PrimalstinctLifecycle.isManaged(player) ? PrimalstinctCodexText.instinctsContent(player) : null;
    }

    @Override
    public CodexInstinctColumnHooks.BottomTexture bottomTexture(PlayerEntity player) {
        return PrimalstinctLifecycle.isManaged(player) ? TITLE_TEXTURE : null;
    }
}
