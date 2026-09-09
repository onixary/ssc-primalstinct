package net.onixary.sscPrimalstinct.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Native button interaction/background, with an icon in place of its visible label. */
public final class InstinctPageButton extends ButtonWidget {
    private static final Identifier ICON = new Identifier("ssc-primalstinct", "textures/gui/instinct_button.png");
    private final int scale;

    public InstinctPageButton(int centerX, int y, int scale, PressAction onPress) {
        super(centerX - 11 * scale, y, 22 * scale, 22 * scale,
                Text.translatable("codex.ssc-primalstinct.page.open"), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.scale = scale;
        setTooltip(Tooltip.of(getMessage()));
    }

    @Override
    public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
        RenderSystem.enableBlend();
        context.setShaderColor(1, 1, 1, active ? alpha : alpha * 0.5f);
        try {
            context.drawTexture(ICON, getX() + 2 * scale, getY() + 2 * scale,
                    0, 0, 18 * scale, 18 * scale, 18 * scale, 18 * scale);
        } finally {
            context.setShaderColor(1, 1, 1, 1);
        }
    }
}
