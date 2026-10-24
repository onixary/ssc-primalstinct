package net.onixary.sscPrimalstinct.mixin.ssc.client;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateController.TransformingController;
import net.onixary.sscPrimalstinct.feral.RegFeralComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransformingController.class, remap = false)
public abstract class FeralTransformAnimationMixin {
    @Shadow private static AnimationHolder anim_on_transform_normal_to_feral;
    @Shadow private static AnimationHolder anim_on_transform_feral_to_normal;

    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private void primalstinct$enabled(PlayerEntity player, AnimSystem.AnimSystemData data, CallbackInfoReturnable<Boolean> cir) {
        if (RegFeralComponent.FERAL.get(player).transitioning()) cir.setReturnValue(true);
    }
    @Inject(method = "getAnimation", at = @At("HEAD"), cancellable = true)
    private void primalstinct$animation(PlayerEntity player, AnimSystem.AnimSystemData data, CallbackInfoReturnable<AnimationHolder> cir) {
        var component = RegFeralComponent.FERAL.get(player);
        if (!component.transitioning()) return;
        cir.setReturnValue(component.restoring() ? anim_on_transform_feral_to_normal : anim_on_transform_normal_to_feral);
    }
}
