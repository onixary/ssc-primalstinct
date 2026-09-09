package net.onixary.sscPrimalstinct.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Wrapped text with wheel, keyboard and draggable scrollbar; scissor never escapes this column. */
final class PrimalstinctTextWidget extends ClickableWidget {
    private final TextRenderer renderer;
    private static final int VERTICAL_PADDING = 4;
    private final List<OrderedText> lines;
    private final int lineHeight;
    private double scroll;
    private boolean draggingScrollbar;

    PrimalstinctTextWidget(int x, int y, int width, int height, Text heading, Text content, TextRenderer renderer) {
        super(x, y, width, height, heading.copy().append("\n").append(content));
        this.renderer = renderer;
        this.lineHeight = renderer.fontHeight + 3;
        this.lines = renderer.wrapLines(content, Math.max(1, width - 16));
    }

    private int viewportTop() { return getY() + VERTICAL_PADDING; }
    private int viewportHeight() { return Math.max(1, height - VERTICAL_PADDING * 2); }
    private int maxScroll() { return Math.max(0, lines.size() * lineHeight - viewportHeight()); }
    private void scrollTo(double value) { scroll = MathHelper.clamp(value, 0, maxScroll()); }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        int top = viewportTop();
        context.enableScissor(getX() + 4, top, getX() + width - 8, top + viewportHeight());
        try {
            int first = (int) scroll / lineHeight;
            int last = Math.min(lines.size(), first + viewportHeight() / lineHeight + 2);
            for (int i = first; i < last; i++) {
                context.drawText(renderer, lines.get(i), getX() + 5, top + i * lineHeight - (int) scroll, 0x302B25, false);
            }
        } finally {
            context.disableScissor();
        }
        if (maxScroll() > 0) {
            int thumb = thumbHeight();
            int thumbY = top + (int) (scroll / maxScroll() * (viewportHeight() - thumb));
            context.fill(getX() + width - 5, top, getX() + width - 2, top + viewportHeight(), 0x306B5740);
            context.fill(getX() + width - 5, thumbY, getX() + width - 2, thumbY + thumb, 0xFF9B8057);
        }
    }

    private int thumbHeight() {
        return Math.min(viewportHeight(), Math.max(8, viewportHeight() * viewportHeight() / (lines.size() * lineHeight)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() == 0) return false;
        scrollTo(scroll - amount * lineHeight * 3);
        return true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        draggingScrollbar = maxScroll() > 0 && mouseX >= getX() + width - 8;
        if (draggingScrollbar) dragTo(mouseY);
    }

    private void dragTo(double mouseY) {
        int travel = viewportHeight() - thumbHeight();
        if (travel > 0) scrollTo((mouseY - viewportTop() - thumbHeight() / 2.0) / travel * maxScroll());
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (draggingScrollbar) dragTo(mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) { draggingScrollbar = false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> scrollTo(scroll - lineHeight);
            case GLFW.GLFW_KEY_DOWN -> scrollTo(scroll + lineHeight);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollTo(scroll - viewportHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTo(scroll + viewportHeight());
            case GLFW.GLFW_KEY_HOME -> scrollTo(0);
            case GLFW.GLFW_KEY_END -> scrollTo(maxScroll());
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }
}
