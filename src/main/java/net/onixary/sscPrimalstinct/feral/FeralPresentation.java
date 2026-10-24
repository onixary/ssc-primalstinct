package net.onixary.sscPrimalstinct.feral;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.player_form.*;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.*;
import java.util.Set;

/** Never registered or installed in PlayerFormComponent: only returned to render consumers. */
public final class FeralPresentation extends Form_FeralBase {
    private final IForm original;
    private final IForm model;
    private final IForm animation;
    private final Identifier modelOrigin;

    public FeralPresentation(IForm original, FeralProfiles.Profile profile) {
        super(original.getFormID());
        this.original = original;
        this.modelOrigin = profile.modelOrigin();
        // Same builder as SSC, also records the eye baseline used by held-item rendering.
        this.applyScale(profile.scale(), profile.eyeScale());
        this.model = profile.modelForm() == null ? original : RegPlayerForms.getPlayerForm(profile.modelForm());
        this.animation = profile.animationForm() == null ? null : RegPlayerForms.getPlayerForm(profile.animationForm());
    }

    @Override public Set<String> getFormFlag() { return original.getFormFlag(); }
    @Override public int getFormTier() { return original.getFormTier(); }
    @Override public IFormGroup getFormGroup() { return original.getFormGroup(); }
    @Override public Identifier getPerkTreeID() { return original.getPerkTreeID(); }
    @Override public Pair<Identifier, Identifier> getFormLayer() { return original.getFormLayer(); }
    @Override public Pair<Identifier, Identifier> getRenderLayerOverride() {
        if (modelOrigin != null) return new Pair<>(new Identifier("origins", "origin"), modelOrigin);
        IForm chosen = model == null ? original : model;
        var override = chosen.getRenderLayerOverride();
        return override == null ? chosen.getFormLayer() : override;
    }
    @Override public AbstractAnimStateController getAnimStateController(PlayerEntity player, AnimSystem.AnimSystemData data, Identifier state) {
        return animation == null ? super.getAnimStateController(player, data, state) : animation.getAnimStateController(player, data, state);
    }
    // Keep ability-specific animations and their completion callbacks on the real form.
    @Override public void registerPowerAnim(PlayerEntity player, AnimSystem.AnimSystemData data) { original.registerPowerAnim(player, data); }
    @Override public boolean isPowerAnimRegistered(PlayerEntity player, AnimSystem.AnimSystemData data) { return original.isPowerAnimRegistered(player, data); }
    @Override public Pair<Boolean, AnimationHolder> getPowerAnim(PlayerEntity player, AnimSystem.AnimSystemData data, Identifier id) {
        return original.getPowerAnim(player, data, id);
    }
}
