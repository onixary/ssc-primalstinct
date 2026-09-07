package net.onixary.sscPrimalstinct.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter;
import net.onixary.sscPrimalstinct.component.PrimalstinctComponent;
import net.onixary.sscPrimalstinct.data.PrimalFormProfile;
import net.onixary.sscPrimalstinct.data.PrimalRoster;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctService;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctSource;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctStateManager;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;

/**
 * 卡01 骨架 + 卡03 组件语义：管理员 debug 命令。
 * value/locked 写入 CCA 组件（唯一权威数据源）并立即 S2C 同步；level 由等级表派生，不再可直写。
 * 后续扩展项：power source、限制槽位（卡07/09）。
 */
public final class PrimalstinctCommands {

    private PrimalstinctCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("primalstinct")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("debug")
                        .then(CommandManager.literal("get")
                                .executes(context -> executeGet(context, context.getSource().getPlayer()))
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(context -> executeGet(context, EntityArgumentType.getPlayer(context, "target")))))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.literal("value")
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .then(CommandManager.argument("value", FloatArgumentType.floatArg(0.0f))
                                                        .executes(PrimalstinctCommands::executeSetValue))))
                                .then(CommandManager.literal("locked")
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .then(CommandManager.argument("locked", BoolArgumentType.bool())
                                                        .executes(PrimalstinctCommands::executeSetLocked)))))
                        .then(CommandManager.literal("rate")
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .then(CommandManager.argument("key", StringArgumentType.word())
                                                        .then(CommandManager.argument("points_per_second", FloatArgumentType.floatArg())
                                                                .executes(PrimalstinctCommands::executeRateSet)))))
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .then(CommandManager.argument("key", StringArgumentType.word())
                                                        .executes(PrimalstinctCommands::executeRateRemove))))
                                .then(CommandManager.literal("list")
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .executes(PrimalstinctCommands::executeRateList))))
                        .then(CommandManager.literal("reconcile")
                                .executes(context -> executeReconcile(context, context.getSource().getPlayer()))
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(context -> executeReconcile(context, EntityArgumentType.getPlayer(context, "target")))))
                        .then(CommandManager.literal("ssc")
                                .executes(context -> executeSsc(context, context.getSource().getPlayer()))
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(context -> executeSsc(context, EntityArgumentType.getPlayer(context, "target")))))
                        .then(CommandManager.literal("roster")
                                .executes(PrimalstinctCommands::executeRoster))
                        .then(CommandManager.literal("powers")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(PrimalstinctCommands::executePowers)))
                        .then(CommandManager.literal("sources")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(PrimalstinctCommands::executeSources)))
                        .then(CommandManager.literal("inv")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(PrimalstinctCommands::executeInv)))
                        .then(CommandManager.literal("select")
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .then(CommandManager.argument("form_id", StringArgumentType.greedyString())
                                                .executes(PrimalstinctCommands::executeAdminSelect))))
                        .then(CommandManager.literal("resolve")
                                .then(CommandManager.argument("form_id", StringArgumentType.greedyString())
                                        .executes(PrimalstinctCommands::executeResolve)))));
    }

    private static int executeGet(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        if (target == null) {
            context.getSource().sendError(Text.translatable("ssc-primalstinct.debug.needs_player"));
            return 0;
        }
        PrimalstinctComponent component = PrimalstinctStateManager.component(target);
        PrimalRoster roster = PrimalRosterManager.active();
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.get.header", target.getDisplayName()), false);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.get.line",
                String.format("%.2f", component.getValue()),
                component.getLevel(),
                roster.levels.maxLevel(),
                component.isLocked()), false);
        context.getSource().sendFeedback(() -> Text.literal(String.format(
                "  selection=%s selected=%s schema=%d", component.isSelectionCompleted(),
                component.getSelectedFormId(), component.getSchemaVersion())), false);
        context.getSource().sendFeedback(() -> Text.literal(String.format(
                "  managed=%s initialized=%s entryHandled=%s chooseOnStart=%s pending=%s",
                net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(target),
                component.isInstinctInitialized(), component.isEntryHandled(),
                net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig.chooseFormOnStart(),
                net.onixary.sscPrimalstinct.selection.SelectionSessionManager.isPending(target))), false);
        if (SSCAdapter.isLoaded()) {
            SSCAdapter.SscInstinctSnapshot snapshot = SSCAdapter.readInstinct(target);
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.get.ssc",
                    snapshot.formId() == null ? "?" : snapshot.formId(),
                    String.format("%.4f", snapshot.instinctValue()),
                    String.format("%.4f", snapshot.instinctRate()),
                    snapshot.instinctLock()), false);
        }
        return 1;
    }

    private static int executeSetValue(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        float value = FloatArgumentType.getFloat(context, "value");
        PrimalstinctComponent component = PrimalstinctStateManager.component(target);
        float delta = value - component.getValue();
        boolean applied = PrimalstinctService.modify(target, PrimalstinctSource.ADMIN, delta);
        if (!applied) {
            context.getSource().sendError(Text.translatable("ssc-primalstinct.debug.set.rejected"));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.set.done", "value", target.getDisplayName(),
                String.format("%.2f", component.getValue())), false);
        return 1;
    }

    private static int executeRateSet(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        String key = StringArgumentType.getString(context, "key");
        float rate = FloatArgumentType.getFloat(context, "points_per_second");
        PrimalstinctService.setRate(target, key, PrimalstinctSource.ADMIN, rate);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.rate.set", key, target.getDisplayName(), rate), false);
        return 1;
    }

    private static int executeRateRemove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        String key = StringArgumentType.getString(context, "key");
        PrimalstinctService.removeRate(target, key);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.rate.removed", key, target.getDisplayName()), false);
        return 1;
    }

    private static int executeRateList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        java.util.List<net.onixary.sscPrimalstinct.instinct.PrimalstinctKernel.Contribution> rates =
                PrimalstinctService.listRates(target);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.rate.list_header", target.getDisplayName(),
                PrimalstinctService.currentRate(target)), false);
        for (net.onixary.sscPrimalstinct.instinct.PrimalstinctKernel.Contribution rate : rates) {
            context.getSource().sendFeedback(() -> Text.literal(String.format(
                    "  %s = %.5f/s (%s)", rate.key(), rate.pointsPerSecond(), rate.source())), false);
        }
        return rates.size();
    }

    private static int executeSetLocked(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        boolean locked = BoolArgumentType.getBool(context, "locked");
        PrimalstinctComponent component = PrimalstinctStateManager.component(target);
        component.setLocked(locked);
        PrimalstinctNetwork.syncNow(target);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.set.done", "locked", target.getDisplayName(), locked), false);
        return 1;
    }

    private static int executeReconcile(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        if (target == null) {
            context.getSource().sendError(Text.translatable("ssc-primalstinct.debug.needs_player"));
            return 0;
        }
        boolean changed = PrimalstinctStateManager.reconcile(target);
        if (!changed) {
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.reconcile.no_change", target.getDisplayName()), false);
            return 0;
        }
        PrimalstinctComponent component = PrimalstinctStateManager.component(target);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.reconcile.done", target.getDisplayName(),
                String.format("%.2f", component.getValue()), component.getLevel(), component.isLocked(), true), false);
        return 1;
    }

    private static int executeSsc(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        if (!SSCAdapter.isLoaded()) {
            context.getSource().sendError(Text.translatable("ssc-primalstinct.debug.ssc.not_loaded"));
            return 0;
        }
        if (target == null) {
            context.getSource().sendError(Text.translatable("ssc-primalstinct.debug.needs_player"));
            return 0;
        }
        try {
            SSCAdapter.SscInstinctSnapshot snapshot = SSCAdapter.readInstinct(target);
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.ssc.header", target.getDisplayName()), false);
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.ssc.line",
                    snapshot.formId() == null ? "?" : snapshot.formId(),
                    String.format("%.4f", snapshot.instinctValue()),
                    String.format("%.4f", snapshot.instinctRate()),
                    snapshot.instinctLock()), false);
            return 1;
        } catch (Throwable t) {
            context.getSource().sendError(Text.translatable(
                    "ssc-primalstinct.debug.ssc.read_failed", t.toString()));
            return 0;
        }
    }

    /** 卡02 验收：名单顺序确定、非 FERAL 入选、子形态兜底可见、错误不产生半更新。 */
    private static int executeRoster(CommandContext<ServerCommandSource> context) {
        PrimalRoster roster = PrimalRosterManager.active();
        StringBuilder thresholds = new StringBuilder();
        for (float t : roster.levels.thresholds) {
            if (thresholds.length() > 0) {
                thresholds.append('/');
            }
            thresholds.append(t);
        }
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.roster.header",
                roster.revision, roster.orderedSelectable.size(), roster.profiles.size()), false);
        context.getSource().sendFeedback(() -> Text.literal("  levels: L0..L" + roster.levels.maxLevel()
                + " thresholds=[" + thresholds + "] max=" + roster.levels.maxValue
                + " lockAtMax=" + roster.levels.lockAtMax), false);
        for (PrimalFormProfile profile : roster.orderedSelectable) {
            SSCAdapter.SscFormInfo info = SSCAdapter.formInfo(profile.formId);
            String fallback = profile.fallbackForm == null
                    ? (info != null && info.masterFormId() != null
                        ? info.masterFormId() + " (master)" : PrimalRosterManager.DEFAULT_MAIN_FALLBACK + " (default)")
                    : profile.fallbackForm + " (explicit)";
            String kind = info == null ? "?" : (info.subForm() ? "sub(master=" + info.masterFormId() + ")" : "main");
            context.getSource().sendFeedback(() -> Text.literal(String.format(
                    "  [%d] %s (%s) fallback=%s source=%s",
                    profile.order, profile.formId, kind, fallback, profile.sourceFile)), false);
        }
        java.util.List<String> parseErrors = PrimalRosterManager.lastParseErrors();
        java.util.List<String> validationErrors = PrimalRosterManager.lastValidationErrors();
        if (parseErrors != null) {
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.roster.parse_errors", parseErrors.size()), false);
            for (String error : parseErrors) {
                context.getSource().sendFeedback(() -> Text.literal("  ! " + error), false);
            }
        }
        if (validationErrors != null) {
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.roster.validation_errors", validationErrors.size()), false);
            for (String error : validationErrors) {
                context.getSource().sendFeedback(() -> Text.literal("  ! " + error), false);
            }
        }
        return roster.orderedSelectable.isEmpty() ? 0 : 1;
    }

    /** 卡06 验收观测：按来源列出玩家当前 power（附属来源 + 当前形态 origin 来源）。 */
    private static int executePowers(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        io.github.apace100.apoli.component.PowerHolderComponent holder =
                io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(target);
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.powers.header", target.getDisplayName(),
                holder.getPowerTypes(false).size()), false);
        for (int level = 1; level <= PrimalRosterManager.active().levels.maxLevel(); level++) {
            listSourcePowers(context, holder, net.onixary.sscPrimalstinct.power.PowerPlanResolver.levelSource(level));
        }
        listSourcePowers(context, holder, net.onixary.sscPrimalstinct.power.PowerPlanResolver.FORM_BASE_SOURCE);
        Identifier formId = SSCAdapter.currentFormIdentifier(target);
        if (formId != null) {
            Identifier origin = SSCAdapter.formLayerSource(formId);
            if (origin != null) {
                listSourcePowers(context, holder, origin);
            }
        }
        return 1;
    }

    private static void listSourcePowers(CommandContext<ServerCommandSource> context,
                                         io.github.apace100.apoli.component.PowerHolderComponent holder,
                                         Identifier source) {
        var powers = holder.getPowersFromSource(source);
        if (powers.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("  " + source + ": (none)"), false);
            return;
        }
        for (var type : powers) {
            context.getSource().sendFeedback(() -> Text.literal("  " + source + " -> " + type.getIdentifier()), false);
        }
    }

    /** 卡07 观测：列出每个速率来源、当前有效性（锁定抑制标注）与合并速率。 */
    private static int executeSources(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        PrimalstinctComponent component = PrimalstinctStateManager.component(target);
        boolean locked = component.isLocked();
        context.getSource().sendFeedback(() -> Text.literal(String.format(
                "Rate sources of %s (merged %.5f/s, locked=%s):",
                target.getDisplayName().getString(), PrimalstinctService.currentRate(target), locked)), false);
        for (net.onixary.sscPrimalstinct.instinct.PrimalstinctKernel.Contribution rate : PrimalstinctService.listRates(target)) {
            boolean suppressed = locked
                    && !(rate.pointsPerSecond() < 0 && rate.source() != net.onixary.sscPrimalstinct.instinct.PrimalstinctSource.POWER);
            context.getSource().sendFeedback(() -> Text.literal(String.format(
                    "  %s = %.5f/s (%s)%s", rate.key(), rate.pointsPerSecond(), rate.source(),
                    suppressed ? "  [suppressed: locked]" : "")), false);
        }
        return 1;
    }

    /** 卡09 观测：锁槽规则、受影响槽位、暂存内容与库存摘要（守恒验收工具）。 */
    private static int executeInv(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        var rule = net.onixary.sscPrimalstinct.inventory.InventoryLockManager.rule(target);
        if (rule == null) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "Inventory rule of " + target.getDisplayName().getString() + ": (not derived yet)"), false);
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal(String.format(
                "Inventory rule of %s: hotbar %d/%d, main %d/%d (restricted=%s)",
                target.getDisplayName().getString(),
                rule.allowedHotbarSlots(), net.onixary.sscPrimalstinct.inventory.InventoryLockRule.HOTBAR_SIZE,
                rule.allowedMainSlots(), net.onixary.sscPrimalstinct.inventory.InventoryLockRule.MAIN_SIZE,
                rule.isRestricted())), false);
        // 诊断：实时派生 + 原始 power 实例计数
        var live = io.github.apace100.apoli.component.PowerHolderComponent.getPowers(
                target, net.onixary.sscPrimalstinct.power.factory.RestrictHotbarPower.class);
        var liveInv = io.github.apace100.apoli.component.PowerHolderComponent.getPowers(
                target, net.onixary.sscPrimalstinct.power.factory.RestrictInventoryPower.class);
        context.getSource().sendFeedback(() -> Text.literal(String.format(
                "  probe: hotbarPowers=%d (active=%d) invPowers=%d (active=%d)",
                live.size(), live.stream().filter(p -> p.isActive()).count(),
                liveInv.size(), liveInv.stream().filter(p -> p.isActive()).count())), false);
        // 库存摘要（0-35 非空槽）
        var inv = target.getInventory();
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isEmpty()) {
                count++;
                summary.append(String.format("%n  slot %d%s: %s x%d", i,
                        rule.isLocked(i) ? "[LOCKED]" : "", inv.getStack(i).getItem().toString(),
                        inv.getStack(i).getCount()));
            }
        }
        String finalSummary = count == 0 ? "(empty 0-35)" : summary.toString();
        context.getSource().sendFeedback(() -> Text.literal("  inv:" + finalSummary), false);
        // 暂存
        var stash = PrimalstinctStateManager.component(target).getStash();
        String stashText = stash.isEmpty() ? "(none)" : "";
        StringBuilder stashBuilder = new StringBuilder(stashText);
        for (var entry : stash) {
            stashBuilder.append(String.format("%n  stash slot %d: %s x%d", entry.slot(),
                    entry.stack().getItem().toString(), entry.stack().getCount()));
        }
        String finalStash = stashBuilder.toString();
        context.getSource().sendFeedback(() -> Text.literal("  stash:" + finalStash), false);
        return 1;
    }

    private static int executeAdminSelect(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
        String raw = StringArgumentType.getString(context, "form_id");
        Identifier formId = Identifier.tryParse(raw);
        if (formId == null) { context.getSource().sendError(Text.literal("非法 FormID: " + raw)); return 0; }
        boolean ok = net.onixary.sscPrimalstinct.selection.SelectionSessionManager.adminForceSelect(target, formId);
        context.getSource().sendFeedback(() -> Text.literal(
                ok ? "已强制 " + target.getDisplayName().getString() + " 选择 " + formId
                   : target.getDisplayName().getString() + " 不在 pending 状态或选择失败"), false);
        return ok ? 1 : 0;
    }

    private static int executeResolve(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "form_id");
        Identifier formId = Identifier.tryParse(raw);
        if (formId == null) {
            context.getSource().sendError(Text.literal("非法 FormID: " + raw));
            return 0;
        }
        SSCAdapter.SscFormInfo info = SSCAdapter.formInfo(formId);
        if (info == null) {
            context.getSource().sendError(Text.literal("SSC 中不存在形态: " + formId));
            return 0;
        }
        PrimalFormProfile profile = PrimalRosterManager.resolve(formId);
        if (profile == null) {
            context.getSource().sendFeedback(() -> Text.translatable(
                    "ssc-primalstinct.debug.resolve.none", formId,
                    info.subForm() ? "sub(master=" + info.masterFormId() + ")" : "main"), false);
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.translatable(
                "ssc-primalstinct.debug.resolve.header", formId), false);
        context.getSource().sendFeedback(() -> Text.literal(String.format(
                "  profileForm=%s selectable=%s order=%d fallback=%s diet=%s sleep=%s source=%s",
                profile.formId, profile.selectable, profile.order,
                profile.fallbackForm != null ? profile.fallbackForm : "(dynamic)",
                profile.dietProfile, profile.sleepProfile, profile.sourceFile)), false);
        if (profile.dietProfile != null) {
            var diet = PrimalRosterManager.active().diets.get(profile.dietProfile);
            String dietLine = diet == null
                    ? "  diet: " + profile.dietProfile + " (无档案，行为由 Power 决定)"
                    : String.format("  diet: %s normal=%.1f(%s) unsuitable=%.1f(%s) forbidden=%s [%s]",
                    diet.id(), diet.normalDelta(), diet.normalTag(),
                    diet.unsuitableDelta(), diet.unsuitableTag(),
                    diet.forbiddenTag() == null ? "(无)" : diet.forbiddenTag(), diet.sourceFile());
            String finalLine = dietLine;
            context.getSource().sendFeedback(() -> Text.literal(finalLine), false);
        }
        return 1;
    }
}
