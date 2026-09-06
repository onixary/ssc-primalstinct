package net.onixary.sscPrimalstinct.client.selection;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimRegistries;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimRegistry;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;

/** Screen-owned first-frame Idle pose. Never advances the animation or changes the player's controller. */
final class FormIdlePreview implements IAnimation {
    private IForm form;
    private KeyframeAnimationPlayer animation;

    void prepare(PlayerEntity player, IForm selectedForm) {
        var data = new AnimSystem.AnimSystemData(player);
        data.playerForm = selectedForm;
        var controller = selectedForm.getAnimStateController(player, data, AnimRegistries.ANIM_STATE_IDLE);
        if (controller == null) controller = AnimRegistry.getAnimState(AnimRegistries.ANIM_STATE_IDLE).defaultController;
        if (!controller.isRegistered(player, data)) controller.registerAnim(player, data);
        var holder = controller.getAnimation(player, data);
        var keyframes = holder == null ? null : holder.getAnimation();
        if (form != selectedForm || (animation == null ? keyframes != null : animation.getData() != keyframes)) {
            form = selectedForm;
            animation = keyframes == null ? null : new KeyframeAnimationPlayer(keyframes, 0);
        }
        if (animation != null) {
            animation.setupAnim(0);
        }
    }

    @Override public boolean isActive() { return true; }
    @Override public void setupAnim(float ignored) {
        if (animation != null) animation.setupAnim(0);
    }
    @Override public Vec3f get3DTransform(String bone, TransformType type, float ignored, Vec3f original) {
        // Do not inherit locomotion/skill transforms on bones not keyed by this Idle clip.
        Vec3f neutral = new Vec3f(0, 0, 0);
        return animation == null ? neutral : animation.get3DTransform(bone, type, 0, neutral);
    }
}
