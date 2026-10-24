package net.onixary.sscPrimalstinct.mixin.ssc.client;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import net.onixary.sscPrimalstinct.feral.FeralPresentation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = FormRenderUtils.class, remap = false)
public abstract class FeralModelMixin {
    @Inject(method = "getPlayerAllFormRenderer", at = @At("HEAD"), cancellable = true)
    private static void primalstinct$model(PlayerEntity player, CallbackInfoReturnable<List<FormRenderer>> cir) {
        IForm form = FormTextureUtils.getPlayerForm_Render(player);
        if (form instanceof FeralPresentation) {
            var layer = form.getRenderLayerOverride();
            var renderer = FormRenderUtils.getFormRenderer(layer.getLeft(), layer.getRight());
            if (renderer != null) cir.setReturnValue(List.of(renderer));
        }
    }
}
