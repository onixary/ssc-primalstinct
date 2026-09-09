package net.onixary.sscPrimalstinct.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.custom_ui.CodexInstinctColumnHooks;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;

/**
 * 卡16：SSC 图鉴第二页 INSTINCTS 列的附属侧 Provider。
 * 迁移自原 S7 mixin（对 P2.init/render 的注入）：SSC 1.10.0 新增公开扩展点
 * CodexInstinctColumnHooks（本接口即 SSC 的公开 API，非内部类引用），
 * 新本能页：该列仅保留统一提示，入口按钮由 SSCClientAdapter 的 Fabric 界面事件添加。
 * 非管理形态全部返回 null，页面回退原版 CodexData 行为。
 */
@Environment(EnvType.CLIENT)
public final class PrimalstinctCodexColumnProvider implements CodexInstinctColumnHooks.Provider {

    public static final PrimalstinctCodexColumnProvider INSTANCE = new PrimalstinctCodexColumnProvider();

    private PrimalstinctCodexColumnProvider() {
    }

    @Override
    public Text instinctsDesc(PlayerEntity player) {
        return player != null && PrimalstinctLifecycle.isManaged(player)
                ? Text.translatable("codex.ssc-primalstinct.instincts.open_hint") : null;
    }

    @Override
    public Text instinctsContent(PlayerEntity player) {
        return player != null && PrimalstinctLifecycle.isManaged(player) ? Text.empty() : null;
    }

    @Override
    public CodexInstinctColumnHooks.BottomTexture bottomTexture(PlayerEntity player) {
        return null;
    }
}
