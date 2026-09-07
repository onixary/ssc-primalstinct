package net.onixary.sscPrimalstinct.client.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.onixary.sscPrimalstinct.client.PrimalstinctClientConfig;
import net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig;

import java.util.function.Supplier;

/**
 * 卡17：ModMenu 聚合配置菜单（SSC ConfigMenuScreen 同款模式——ModMenu 每模组仅一个配置入口，
 * 多个 AutoConfig 屏幕经此枢纽打开）。cloth-config 缺失时按钮禁用并提示
 * （SSC 1.10.0 正式包不内嵌 cloth，不能假设运行时必在）。
 */
@Environment(EnvType.CLIENT)
public class PrimalstinctConfigMenuScreen extends Screen {

    private final Screen parent;
    private static final boolean CLOTH_PRESENT =
            FabricLoader.getInstance().isModLoaded("cloth-config")
                    || FabricLoader.getInstance().isModLoaded("cloth-config2");

    public PrimalstinctConfigMenuScreen(Screen parent) {
        super(Text.translatable("text.ssc-primalstinct.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 240;
        int buttonHeight = 20;
        int interval = 10;
        int count = 3;  // 客户端 / 服务端 / 关闭
        int x = (this.width - buttonWidth) / 2;
        int y = (this.height - buttonHeight * count - interval * (count - 1)) / 2;

        // 客户端配置（本能条位置）：保存即时生效
        addConfigButton(x, y, buttonWidth, buttonHeight,
                Text.translatable("text.ssc-primalstinct.config.client"),
                () -> AutoConfig.getConfigScreen(PrimalstinctClientConfig.class, this).get());
        y += buttonHeight + interval;

        // 服务端配置（开局选择路线）：重启/重进世界生效；联机时仅服务器本地文件权威
        addConfigButton(x, y, buttonWidth, buttonHeight,
                Text.translatable("text.ssc-primalstinct.config.server"),
                () -> AutoConfig.getConfigScreen(PrimalstinctServerConfig.class, this).get());
        y += buttonHeight + interval;

        // 关闭
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("text.ssc-primalstinct.config.close"), button -> close())
                .size(buttonWidth, buttonHeight).position(x, y).build());
    }

    private void addConfigButton(int x, int y, int width, int height, Text label, Supplier<Screen> screenSupplier) {
        ButtonWidget.Builder builder = ButtonWidget.builder(label, button -> {
            if (CLOTH_PRESENT) {
                MinecraftClient.getInstance().setScreen(screenSupplier.get());
            }
        }).size(width, height).position(x, y);
        if (!CLOTH_PRESENT) {
            builder = builder.tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                    Text.translatable("text.ssc-primalstinct.config.requires_cloth")));
        }
        addDrawableChild(builder.build());
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        if (!CLOTH_PRESENT) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("text.ssc-primalstinct.config.requires_cloth"),
                    this.width / 2, this.height - 24, 0xFFFF5555);
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("text.ssc-primalstinct.config.server_hint"),
                this.width / 2, this.height - 12, 0xFFAAAAAA);
    }
}
