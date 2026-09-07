package net.onixary.sscPrimalstinct.selection;

import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;
import net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalRoster;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 卡12：首次选择的服务端事务。服务器掌握待选状态和选择结果，UI 只是操作入口。
 * pending 期间忽略 SSC 默认形态；confirm 幂等（非 pending / 过期 nonce / revision 不匹配均拒绝）。
 */
public final class SelectionSessionManager {

    public record SelectionSession(long nonce, int revision, Identifier defaultForm) {
    }

    private static final Map<UUID, SelectionSession> PENDING = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NONCE_GEN =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    private SelectionSessionManager() {
    }

    public static boolean isPending(ServerPlayerEntity player) {
        return PENDING.containsKey(player.getUuid());
    }

    public static @Nullable SelectionSession getSession(ServerPlayerEntity player) {
        return PENDING.get(player.getUuid());
    }

    /** 登录后（SSC/Apoli/profile 就绪）调用：未完成选择 → 进入 pending 并下发名单。 */
    public static void onPlayerReady(ServerPlayerEntity player) {
        PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
        if (!net.onixary.sscPrimalstinct.instinct.EntryPolicy.needsSelection(
                PrimalstinctServerConfig.chooseFormOnStart(), component.isEntryHandled(), component.isSelectionCompleted())) {
            component.setEntryHandled(true);
            PENDING.remove(player.getUuid());
            PrimalstinctLifecycle.refresh(player);
            return;
        }
        PrimalRoster roster = PrimalRosterManager.active();
        List<Identifier> forms = roster.orderedSelectable.stream().map(p -> p.formId)
                .filter(id -> {
                    var form = net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.getForm(id);
                    return form != null && net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.isFormCanUse(player, form);
                }).toList();
        if (forms.isEmpty()) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 玩家 {} 待选但名单为空", player.getGameProfile().getName());
            return;
        }
        long nonce = NONCE_GEN.incrementAndGet();
        Identifier defaultForm = forms.get(0);
        SelectionSession session = new SelectionSession(nonce, roster.revision, defaultForm);
        PENDING.put(player.getUuid(), session);

        PrimalstinctLifecycle.refresh(player);
        SelectionPackets.sendSelectionList(player, forms, roster.revision, nonce, defaultForm);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 进入形态选择 pending（{} 个可选）",
                player.getGameProfile().getName(), forms.size());
    }

    /** C2S confirm 唯一入口。 */
    public static boolean confirm(ServerPlayerEntity player, Identifier formId, int revision, long nonce) {
        SelectionSession session = PENDING.get(player.getUuid());
        if (!PrimalstinctServerConfig.chooseFormOnStart() || session == null) return false;
        if (session.nonce() != nonce || session.revision() != revision) return false;
        if (formId == null) return false;

        PrimalRoster roster = PrimalRosterManager.active();
        if (roster.revision != revision) return false;

        var profile = roster.profiles.get(formId);
        if (profile == null || !profile.selectable) return false;

        SSCAdapter.SscFormInfo info = SSCAdapter.formInfo(formId);
        if (info == null) return false;

        // 变形
        var form = net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.getForm(formId);
        if (form == null || !net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.isFormCanUse(player, form)) return false;
        net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils._loadForm(player, form);

        // 验证实际最终 FormID
        Identifier actualForm = SSCAdapter.currentFormIdentifier(player);
        if (actualForm == null || PrimalRosterManager.resolve(actualForm) == null) return false;
        formId = actualForm;

        PrimalstinctComponent component = RegPrimalstinctComponent.PRIMALSTINCT.get(player);
        component.setSelectedFormId(formId);
        component.setSelectionCompleted(true);
        component.setEntryHandled(true);

        PENDING.remove(player.getUuid());
        PrimalstinctLifecycle.refresh(player);
        net.onixary.sscPrimalstinct.power.PrimalPowerReconciler.reconcile(player);
        net.onixary.sscPrimalstinct.network.PrimalstinctNetwork.syncNow(player);
        net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.ON_ENABLE_MOD.trigger(player);
        SelectionPackets.sendSelectionConfirmed(player, formId);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 选择 {} 完成", player.getGameProfile().getName(), formId);
        return true;
    }

    public static void onDisconnect(UUID uuid) {
        PENDING.remove(uuid);
    }

    public static boolean adminForceSelect(ServerPlayerEntity player, Identifier formId) {
        SelectionSession session = PENDING.get(player.getUuid());
        return session != null && confirm(player, formId, session.revision(), session.nonce());
    }

    public static void clear() { PENDING.clear(); }

    public static void init() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> onDisconnect(handler.player.getUuid()));
    }
}
