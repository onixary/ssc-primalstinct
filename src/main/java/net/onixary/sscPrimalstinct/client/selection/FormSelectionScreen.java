package net.onixary.sscPrimalstinct.client.selection;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;

public class FormSelectionScreen extends Screen {
    private final ClientSelectionState.SelectionList selection;
    private int selectedIndex = 0;
    private boolean submitted = false;
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 36;
    private static final int MAX_VISIBLE = 6;
    private static final int LIST_WIDTH = 220;
    private static final int PREVIEW_WIDTH = 140;
    private static final int PREVIEW_HEIGHT = MAX_VISIBLE * ENTRY_HEIGHT * 2 / 3;
    private static final int PREVIEW_SCALE = 100;
    private static final float PREVIEW_BASE_YAW = 180.0F + 15.0F;
    private static final float PREVIEW_DOWN_OFFSET = 0.5F;
    private final FormIdlePreview idlePreview = new FormIdlePreview();

    public FormSelectionScreen(ClientSelectionState.SelectionList selection) {
        super(Text.translatable("ssc-primalstinct.selection.title"));
        this.selection = selection;
        for (int i = 0; i < selection.forms.size(); i++) {
            if (selection.forms.get(i).equals(selection.defaultForm)) { selectedIndex = i; break; }
        }
    }

    @Override
    protected void init() {
        super.init();
        int listX = this.width / 2 - (LIST_WIDTH + PREVIEW_WIDTH) / 2;
        int previewX = listX + LIST_WIDTH + 8;
        int buttonY = 30 + MAX_VISIBLE * ENTRY_HEIGHT - 40;
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("ssc-primalstinct.selection.confirm"), b -> submit())
                .dimensions(previewX + PREVIEW_WIDTH / 2 - 65, buttonY, 130, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        if (submitted) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("ssc-primalstinct.selection.waiting"), this.width / 2, this.height / 2, 0xFFAA00);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        int listX = this.width / 2 - (LIST_WIDTH + PREVIEW_WIDTH) / 2;
        renderFormList(context, listX, 26, mouseX, mouseY);
        renderModelPreview(context, listX + LIST_WIDTH + 8, 26, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderFormList(DrawContext context, int x, int y, int mouseX, int mouseY) {
        var forms = selection.forms;
        context.fill(x - 2, y - 2, x + LIST_WIDTH + 2, y + MAX_VISIBLE * ENTRY_HEIGHT + 2, 0x80000000);
        for (int i = 0; i < Math.min(MAX_VISIBLE, forms.size() - scrollOffset); i++) {
            int idx = scrollOffset + i;
            Identifier formId = forms.get(idx);
            int entryY = y + i * ENTRY_HEIGHT;
            boolean sel = idx == selectedIndex;
            boolean hov = mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
            if (sel) context.fill(x, entryY, x + LIST_WIDTH, entryY + ENTRY_HEIGHT, 0x60404080);
            else if (hov) context.fill(x, entryY, x + LIST_WIDTH, entryY + ENTRY_HEIGHT, 0x30FFFFFF);
            String nk = "ssc-primalstinct.form." + formId.getNamespace() + "." + formId.getPath() + ".name";
            String name = Text.translatable(nk).getString();
            if (name.equals(nk)) name = formId.getPath();
            context.drawTextWithShadow(this.textRenderer, name, x + 6, entryY + 4, sel ? 0xFFFFFF : 0xCCCCCC);
            String dk = "ssc-primalstinct.form." + formId.getNamespace() + "." + formId.getPath() + ".description";
            String desc = Text.translatable(dk).getString();
            if (!desc.equals(dk)) {
                if (this.textRenderer.getWidth(desc) > LIST_WIDTH - 12)
                    desc = this.textRenderer.trimToWidth(desc, LIST_WIDTH - 20) + "...";
                context.drawTextWithShadow(this.textRenderer, desc, x + 6, entryY + 16, 0x999999);
            }
        }
        if (forms.size() > MAX_VISIBLE) {
            context.drawTextWithShadow(this.textRenderer, (scrollOffset + 1) + "/" + (forms.size() - MAX_VISIBLE + 1),
                    x + LIST_WIDTH - 20, y + MAX_VISIBLE * ENTRY_HEIGHT + 4, 0x888888);
        }
    }

    private void renderModelPreview(DrawContext context, int x, int y, int mouseX, int mouseY, float delta) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int panelH = MAX_VISIBLE * ENTRY_HEIGHT;
        context.fill(x - 2, y - 2, x + PREVIEW_WIDTH + 2, y + panelH + 2, 0x60000000);
        if (selectedIndex < selection.forms.size()) {
            Identifier formId = selection.forms.get(selectedIndex);
            String nk = "ssc-primalstinct.form." + formId.getNamespace() + "." + formId.getPath() + ".name";
            String name = Text.translatable(nk).getString();
            if (name.equals(nk)) name = formId.getPath();
            context.drawCenteredTextWithShadow(this.textRenderer, name, x + PREVIEW_WIDTH / 2, y + PREVIEW_HEIGHT + 12, 0xFFFFFF);
        }
        if (selectedIndex < 0 || selectedIndex >= selection.forms.size()) return;
        IForm selectedForm = FormUtils.getForm(selection.forms.get(selectedIndex));
        // Dynamic form data can arrive after the selection list. Retry next frame, never show the wrong player form.
        if (selectedForm == null) return;
        int vpX = x, vpY = y, vpW = PREVIEW_WIDTH, vpH = PREVIEW_HEIGHT;
        context.fill(vpX, vpY, vpX + vpW, vpY + vpH, 0x40000000);
        // Same preview hooks as SSC's FormColorSelectMenuV2, scoped to this draw call.
        boolean previousTempModel = FormTextureUtils.useTempFormModel;
        var previousModelProcessor = FormTextureUtils.tempFormModelProcessor;
        boolean previousTempSkin = FormTextureUtils.useTempCustomSkinConfig;
        var previousSkinProcessor = FormTextureUtils.tempCustomSkinConfigOverrider;
        var e = client.player;
        var animationStack = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(e);
        boolean wasSneaking = e.isSneaking();
        var previousPose = e.getPose();
        float previousSwing = e.handSwingProgress;
        float h = e.bodyYaw, i2 = e.getYaw(), j = e.getPitch();
        float k = e.prevHeadYaw, l = e.headYaw, m = e.prevBodyYaw;
        context.enableScissor(vpX, vpY, vpX + vpW, vpY + vpH);
        try {
            e.setSneaking(false);
            e.setPose(net.minecraft.entity.EntityPose.STANDING);
            e.handSwingProgress = 0;
            idlePreview.prepare(e, selectedForm);
            animationStack.addAnimLayer(Integer.MAX_VALUE, idlePreview);
            FormTextureUtils.tempFormModelProcessor = new FormTextureUtils.TempFormModelProcessor() {
                @Override public IForm getForm() { return selectedForm; }
                @Override public Identifier getLayerID() {
                    var override = selectedForm.getRenderLayerOverride();
                    return override == null ? selectedForm.getFormLayer().getRight() : override.getRight();
                }
            };
            FormTextureUtils.useTempFormModel = true;
            FormTextureUtils.tempCustomSkinConfigOverrider = () -> false;
            FormTextureUtils.useTempCustomSkinConfig = true;
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            float f = (float) Math.atan((x + PREVIEW_WIDTH / 2 - mouseX) / 40.0F);
            float g = (float) Math.atan((vpY + vpH / 2 - mouseY) / 40.0F);
            Quaternionf q1 = new Quaternionf().rotateZ(3.1415927F);
            Quaternionf q2 = new Quaternionf().rotateX(g * 20.0F * 0.017453292F);
            q1.mul(q2);
            e.bodyYaw = PREVIEW_BASE_YAW + f * 20.0F;
            e.prevBodyYaw = e.bodyYaw;
            e.setYaw(PREVIEW_BASE_YAW + f * 40.0F);
            e.setPitch(-g * 20.0F);
            e.headYaw = e.getYaw();
            e.prevHeadYaw = e.getYaw();
            InventoryScreen.drawEntity(context, vpX + vpW / 2,
                    vpY + vpH - 8 + Math.round(PREVIEW_DOWN_OFFSET * PREVIEW_SCALE), PREVIEW_SCALE, q1, q2, e);
        } finally {
            animationStack.removeLayer(idlePreview);
            e.setSneaking(wasSneaking);
            e.setPose(previousPose);
            e.handSwingProgress = previousSwing;
            e.bodyYaw = h; e.prevBodyYaw = m;
            e.setYaw(i2); e.setPitch(j);
            e.prevHeadYaw = k; e.headYaw = l;
            FormTextureUtils.useTempFormModel = previousTempModel;
            FormTextureUtils.tempFormModelProcessor = previousModelProcessor;
            FormTextureUtils.useTempCustomSkinConfig = previousTempSkin;
            FormTextureUtils.tempCustomSkinConfigOverrider = previousSkinProcessor;
            context.disableScissor();
        }
    }

    private void submit() {
        if (submitted || selectedIndex >= selection.forms.size()) return;
        Identifier formId = selection.forms.get(selectedIndex);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(formId.toString());
        buf.writeVarInt(selection.revision);
        buf.writeVarLong(selection.nonce);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                net.onixary.sscPrimalstinct.selection.SelectionPackets.CONFIRM_C2S, buf);
        submitted = true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (submitted) return super.mouseClicked(mx, my, button);
        int lx = this.width / 2 - (LIST_WIDTH + PREVIEW_WIDTH) / 2;
        if (mx >= lx && mx < lx + LIST_WIDTH && my >= 26) {
            int idx = scrollOffset + (int) ((my - 26) / ENTRY_HEIGHT);
            if (idx >= 0 && idx < selection.forms.size() && idx < scrollOffset + MAX_VISIBLE) { selectedIndex = idx; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scrollOffset = Math.max(0, Math.min(Math.max(0, selection.forms.size() - MAX_VISIBLE), scrollOffset - (int) amount));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) { selectedIndex = Math.max(0, selectedIndex - 1); ensureVisible(); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) { selectedIndex = Math.min(selection.forms.size() - 1, selectedIndex + 1); ensureVisible(); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible() {
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + MAX_VISIBLE) scrollOffset = selectedIndex - MAX_VISIBLE + 1;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
