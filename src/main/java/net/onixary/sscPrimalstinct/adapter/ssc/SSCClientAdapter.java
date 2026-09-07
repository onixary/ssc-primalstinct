package net.onixary.sscPrimalstinct.adapter.ssc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P2;
import net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenuV2;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.mana.IManaRender;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaRegistriesClient;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.screen_effect.TransformOverlay;

/**
 * 卡15：SSC 客户端类的唯一引用点（adapter/ssc 集中引用原则的客户端侧延伸）。
 * 仅在物理客户端加载（依赖 @Environment(CLIENT) 的 ManaRegistriesClient）。
 */
@Environment(EnvType.CLIENT)
public final class SSCClientAdapter {

    private SSCClientAdapter() {
    }

    /**
     * 当前形态的法力条是否顶替旧本能条位置（InstinctBarLikeManaBar 等 OverrideInstinctBar 实现）。
     * 新本能条不参与 SSC 的顶替链：法力条照常渲染在原位，新本能条整体上移避让，
     * 保证有法力形态同时看到两类资源（白板卡15 验收项）。
     */
    public static boolean manaOverridesInstinctBar() {
        if (!SSCAdapter.isLoaded()) {
            return false;
        }
        IManaRender render = ManaRegistriesClient.getManaRender(ManaComponent.LocalManaTypeID);
        return render != null && render.OverrideInstinctBar();
    }

    /** 高值预警粒子：沿用旧 SSC 的变身粒子（StaticParams.PLAYER_TRANSFORM_PARTICLE）。 */
    public static ParticleEffect transformParticle() {
        return StaticParams.PLAYER_TRANSFORM_PARTICLE;
    }

    // ---- 满值锁定演出：驱动 SSC 变形屏幕叠加层（TransformOverlay 公开 setter，只借视觉效果）----

    public static void transformOverlayEnable(boolean enable) {
        TransformOverlay.INSTANCE.setEnableOverlay(enable);
    }

    public static void transformOverlayNausea(float strength) {
        TransformOverlay.INSTANCE.setNauesaStrength(strength);
    }

    public static void transformOverlayBlack(float strength) {
        TransformOverlay.INSTANCE.setBlackStrength(strength);
    }

    // ---- 卡16：无书快捷访问（调色菜单 / 图鉴开始页 / 图鉴第二页）----

    /** 玩家是否处于 SSC 启用前的原始形态（未开书启用，应打开开始引导页）。 */
    public static boolean isFormBeforeEnable(PlayerEntity player) {
        return FormUtils.getPlayerForm(player).equals(RegPlayerForms.ORIGINAL_BEFORE_ENABLE);
    }

    /** 未启用 SSC 的开始引导页（自带重复打开保护）。 */
    public static void openStartBook(PlayerEntity player) {
        ShapeShifterCurseFabricClient.openStartBookScreen(player);
    }

    /** 图鉴第二页（形态详情页；先赋 currentPlayer 再 setScreen）。管理形态的本能列由 P2 mixin 替换文本。 */
    public static void openCodexSecondPage(PlayerEntity player) {
        BookOfShapeShifterScreenV2_P2 screen = new BookOfShapeShifterScreenV2_P2();
        screen.currentPlayer = player;
        MinecraftClient.getInstance().setScreen(screen);
    }

    /** 形态调色菜单 V2（无需持书/库存槽；父界面为 null，关闭时经原 close 流程保存并向服务端同步颜色）。 */
    public static void openFormColorMenu() {
        MinecraftClient.getInstance().setScreen(new FormColorSelectMenuV2(
                Text.translatable("text.shape-shifter-curse.config.form_color_select_menu"), null));
    }

    /** 卡16：注册图鉴第二页 INSTINCTS 列扩展（SSC 1.10.0 公开扩展点，替代原 S7 mixin）。 */
    public static void registerCodexColumnProvider(net.onixary.sscPrimalstinct.client.ui.PrimalstinctCodexColumnProvider provider) {
        net.onixary.shapeShifterCurseFabric.custom_ui.CodexInstinctColumnHooks.register(provider);
    }
}
