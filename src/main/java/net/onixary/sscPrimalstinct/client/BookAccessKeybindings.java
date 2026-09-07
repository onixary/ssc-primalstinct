package net.onixary.sscPrimalstinct.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCClientAdapter;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import org.lwjgl.glfw.GLFW;

/**
 * 卡16：无书快捷访问的两个独立可重绑定快捷键（默认不绑定，避免与移动/技能冲突）。
 * - 形态调色：直达 FormColorSelectMenuV2，不要求持书或可用库存槽；关闭走原 close 保存/同步流程。
 * - 图鉴页面：未启用 SSC（ORIGINAL_BEFORE_ENABLE）→ 原开始引导页；已启用 → 直达图鉴第二页
 *   （管理形态的本能列由 P2 mixin 替换为新系统文本）。
 * 绕过的只是持书/库存要求；pending、无玩家、已有界面打开时不响应（每次按下只打开一次）。
 */
public final class BookAccessKeybindings {

    private static KeyBinding formColorMenu;
    private static KeyBinding codexPage;

    private BookAccessKeybindings() {
    }

    public static void register() {
        formColorMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ssc-primalstinct.form_color_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,  // 默认不绑定
                "category.ssc-primalstinct"));
        codexPage = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ssc-primalstinct.codex_page",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,  // 默认不绑定
                "category.ssc-primalstinct"));
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null || selectionPending()) {
            return;
        }
        if (formColorMenu != null && formColorMenu.wasPressed()) {
            SSCClientAdapter.openFormColorMenu();
        }
        if (codexPage != null && codexPage.wasPressed()) {
            if (SSCClientAdapter.isFormBeforeEnable(client.player)) {
                SSCClientAdapter.openStartBook(client.player);
            } else {
                SSCClientAdapter.openCodexSecondPage(client.player);
            }
        }
    }

    private static boolean selectionPending() {
        var snapshot = ClientPrimalstinctState.snapshot();
        return snapshot != null && snapshot.selectionPending();
    }
}
