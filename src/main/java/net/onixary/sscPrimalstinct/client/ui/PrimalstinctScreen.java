package net.onixary.sscPrimalstinct.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;

/** Two independently scrollable columns over a replaceable, 5:4 background. */
public final class PrimalstinctScreen extends Screen {
    public static final Identifier BACKGROUND = new Identifier("ssc-primalstinct", "textures/gui/primalstinct_page.png");
    private final Screen parent;
    private Identifier form;
    private int stage = PrimalstinctCodexText.currentLevel();
    private int maxStage;
    private int x, y, panelWidth, panelHeight;
    private float layoutScale;

    private int scaled(int value) { return Math.round(value * layoutScale); }

    public PrimalstinctScreen(Screen parent) {
        super(Text.translatable(PrimalstinctCodexText.PREFIX + "title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client.player == null) return;
        form = SSCAdapter.currentFormIdentifier(client.player);
        if (form == null) return;
        maxStage = PrimalstinctCodexText.maxLevel();
        stage = MathHelper.clamp(stage, 1, maxStage);
        // 150% of the original half-width layout, uniformly fitted to the window.
        int baseWidth = Math.max(100, width / 2);
        layoutScale = Math.min(1.5f, Math.min((width - 32f) / baseWidth,
                (height - 36f) / (baseWidth * 0.8f)));
        panelWidth = Math.round(baseWidth * layoutScale);
        panelHeight = panelWidth * 4 / 5;
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        int gap = scaled(8);
        int columnWidth = (panelWidth - gap * 3) / 2;
        int contentTop = y + gap;
        int leftHeight = Math.max(scaled(24), panelHeight * 7 / 10 - gap);
        int rightHeight = Math.max(scaled(24), panelHeight - scaled(10) - gap);
        int instinctColumnWidth = Math.max(1, Math.round(columnWidth * 0.9f));
        addDrawableChild(new PrimalstinctTextWidget(x + gap, contentTop, instinctColumnWidth, leftHeight,
                Text.translatable(PrimalstinctCodexText.PREFIX + "instincts"),
                PrimalstinctCodexText.section(form, stage, "instincts"), textRenderer));
        addDrawableChild(new PrimalstinctTextWidget(x + gap * 2 + columnWidth, contentTop, columnWidth, rightHeight,
                Text.translatable(PrimalstinctCodexText.PREFIX + "abilities"),
                PrimalstinctCodexText.section(form, stage, "abilities"), textRenderer));
        addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> close())
                .dimensions(x + panelWidth - scaled(12), y - scaled(10), scaled(22), scaled(22))
                .tooltip(Tooltip.of(Text.translatable(PrimalstinctCodexText.PREFIX + "back")))
                .narrationSupplier(supplier -> Text.translatable(PrimalstinctCodexText.PREFIX + "back")).build());
    }

    @Override
    public void tick() {
        if (client.player == null || !ClientPrimalstinctState.managed()) {
            close();
            return;
        }
        Identifier currentForm = SSCAdapter.currentFormIdentifier(client.player);
        if (!java.util.Objects.equals(form, currentForm) || stage != PrimalstinctCodexText.currentLevel()) {
            stage = PrimalstinctCodexText.currentLevel();
            clearAndInit();
        } else if (maxStage != PrimalstinctCodexText.maxLevel()) {
            clearAndInit();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the book visible beneath the modal, without its hover tooltips.
        context.getMatrices().push();
        try {
            parent.render(context, -1, -1, delta);
            context.draw();
        } finally {
            context.getMatrices().pop();
        }
        // Text (including book header shadows) writes depth. Drawing the modal later
        // at the same Z is insufficient: give its shade, background and widgets one
        // foreground layer and flush it before restoring the caller's matrices.
        context.getMatrices().push();
        try {
            context.getMatrices().translate(0, 0, 400);
            renderModal(context, mouseX, mouseY, delta);
            context.draw();
        } finally {
            context.getMatrices().pop();
        }
    }

    private void renderModal(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);
        if (form == null) return;
        RenderSystem.enableBlend();
        context.drawTexture(BACKGROUND, x, y, 0, 0, panelWidth, panelHeight, panelWidth, panelHeight);
        RenderSystem.disableBlend();
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(client.player == null || client.world == null ? null : parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
