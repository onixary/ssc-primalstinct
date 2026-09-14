package net.onixary.sscPrimalstinct.instinct;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.effect.PrimalCallEffect;
import net.onixary.sscPrimalstinct.selection.SelectionSessionManager;
import java.util.Objects;

public final class PrimalCallService {
    private PrimalCallService() {}

    public static boolean eligible(LivingEntity entity) {
        if (!(entity instanceof ServerPlayerEntity player) || !player.isAlive()
                || player.isSpectator() || SelectionSessionManager.isPending(player)
                || RegPrimalstinctComponent.PRIMALSTINCT.get(player).isPrimalAwakened()) return false;
        var form = SSCAdapter.currentFormIdentifier(player);
        return form != null && PrimalRosterManager.resolve(form) != null;
    }

    public static boolean apply(ServerPlayerEntity player) {
        return eligible(player) && !player.hasStatusEffect(PrimalCallEffect.INSTANCE)
                && player.addStatusEffect(new StatusEffectInstance(PrimalCallEffect.INSTANCE,
                        PrimalstinctServerConfig.primalCallDurationTicks()));
    }

    public static void awaken(ServerPlayerEntity player) {
        if (!eligible(player)) return;
        RegPrimalstinctComponent.PRIMALSTINCT.get(player).awakenPrimalInstinct();
        // Rebuild the SSC form so its old powers are reconciled with the new ownership.
        PrimalstinctLifecycle.refresh(player);
        SSCAdapter.rebuildCurrentForm(player);
        // 觉醒演出：黑暗/恶心 + L1 提示与对话（L1 无跨级事件，需在此显式触发）
        PrimalstinctPresentation.playStageEffects(player);
        PrimalstinctPresentation.playAwakening(player);
    }

    public static void register() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, victim) -> {
            if (killer instanceof ServerPlayerEntity player && victim instanceof MobEntity
                    && eligible(player) && !player.hasStatusEffect(PrimalCallEffect.INSTANCE)
                    && player.getRandom().nextFloat() < PrimalstinctServerConfig.primalCallKillChance()) {
                apply(player);
            }
        });
        SSCEvent.FORM_CHANGE_END.register((player, oldForm, newForm) -> {
            if (player instanceof ServerPlayerEntity && !Objects.equals(
                    oldForm == null ? null : oldForm.getFormID(), newForm == null ? null : newForm.getFormID())) {
                player.removeStatusEffect(PrimalCallEffect.INSTANCE);
            }
        });
    }
}
