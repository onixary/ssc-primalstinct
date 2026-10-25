package net.onixary.sscPrimalstinct.feral;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2CServer;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.effect.PlayerTransformEffectManager;
import net.onixary.shapeShifterCurseFabric.player_form.utils.*;
import net.onixary.shapeShifterCurseFabric.status_effects.attachment.EffectManager;

public final class FeralService {
    private FeralService() {}
    public static final int MIDPOINT = StaticParams.TRANSFORM_FX_DURATION_IN;
    public static final int DURATION = MIDPOINT + StaticParams.TRANSFORM_FX_DURATION_OUT;

    private static boolean realTransformation(ServerPlayerEntity player) {
        return PlayerFormComponent.COMPONENT.get(player).transformTargetForm != null
                || InstinctUtils.playerInstinctLock.getOrDefault(player.getUuid(), false);
    }

    public static boolean start(ServerPlayerEntity player, boolean restore) {
        var component = RegFeralComponent.FERAL.get(player);
        var form = FormUtils.getPlayerForm(player);
        var profile = FeralProfiles.get(form.getFormID());
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || component.transitioning()
                || realTransformation(player)) return false;
        if (restore) {
            if (!component.active() || !form.getFormID().equals(component.source())) return false;
            profile = component.profile();
        } else if (component.active() || profile == null || !FormUtils.FinalForm.hasFlag(form)
                || form.getBodyType() == PlayerFormBodyType.FERAL
                || temporaryCursedMoonForm(player)
                || EffectManager.hasTransformativeEffect(player)) {
            return false;
        }
        component.start(form.getFormID(), profile, restore);
        // Reuse SSC's effects and animation broadcast, without calling TransformManager or reloading powers.
        ModPacketsS2CServer.sendTransformState(player, true, form.getFormID(), form.getFormID());
        PlayerTransformEffectManager.applyStartTransformEffect(player, MIDPOINT);
        return true;
    }

    private static boolean temporaryCursedMoonForm(ServerPlayerEntity player) {
        var component = PlayerFormComponent.COMPONENT.get(player);
        // The moon flag is also set for permanent forms which never transformed.
        // Only the recorded moon transformation's current result is temporary.
        return component.isCursedMoonApplied && component.BeforeCursedMoonAppliedForm != null
                && component.AfterCursedMoonAppliedForm != null
                && component.AfterCursedMoonAppliedForm.isPlayerForm(player);
    }

    public static void tick(ServerPlayerEntity player) {
        var component = RegFeralComponent.FERAL.get(player);
        if (component.source() == null) return;
        var current = FormUtils.getPlayerForm(player).getFormID();
        var profile = FeralProfiles.get(current);
        if (!current.equals(component.source()) || profile == null || realTransformation(player)) {
            cancel(player);
            return;
        }
        if (!profile.equals(component.profile())) { component.setProfile(profile); component.sync(); }
        component.ensureScale();
        if (!component.transitioning()) return;
        if (!player.isAlive()) {
            endEffects(player);
            component.finish();
            return;
        }
        if (component.elapsed() >= MIDPOINT && component.active() == component.restoring()) {
            component.applyMidpoint();
            player.sendMessage(Text.translatable("ssc-primalstinct.feral."
                    + (component.restoring() ? "restored" : "applied")), false);
            PlayerTransformEffectManager.applyEndTransformEffect(player, StaticParams.TRANSFORM_FX_DURATION_OUT);
        }
        if (component.elapsed() >= DURATION) {
            endEffects(player);
            component.finish();
        }
    }

    private static void endEffects(ServerPlayerEntity player) {
        // Do not stop a genuine SSC transformation that superseded the cosmetic transition.
        if (!realTransformation(player)) ModPacketsS2CServer.sendTransformState(player, false, null, null);
    }
    public static void cancel(ServerPlayerEntity player) {
        var component = RegFeralComponent.FERAL.get(player);
        if (component.transitioning()) endEffects(player);
        component.clear();
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new FeralProfiles());
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerManager().getPlayerList().forEach(FeralService::tick));
        SSCEvent.FORM_CHANGE_START.register((player, oldForm, newForm) -> {
            if (player instanceof ServerPlayerEntity sp && oldForm != null && !oldForm.isEquals(newForm)
                    && RegFeralComponent.FERAL.get(sp).source() != null) cancel(sp);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var component = RegFeralComponent.FERAL.get(handler.player);
            if (component.transitioning()) {
                endEffects(handler.player);
                component.finish();
            }
        });
    }
}
