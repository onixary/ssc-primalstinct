package net.onixary.sscPrimalstinct;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRosterManager;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRitualConfig;
import net.onixary.sscPrimalstinct.endgame.service.EndgameTeleportService;
import net.onixary.sscPrimalstinct.endgame.service.RemnantService;
import net.onixary.sscPrimalstinct.endgame.service.RitualPortalService;
import net.onixary.sscPrimalstinct.endgame.state.EndgamePlayerComponent;
import net.onixary.sscPrimalstinct.endgame.state.EndgameWorldState;
import net.onixary.sscPrimalstinct.endgame.state.RegEndgameComponent;
import net.onixary.sscPrimalstinct.inventory.InventoryLockManager;
import net.onixary.sscPrimalstinct.items.RegPrimalstinctItems;
import net.onixary.sscPrimalstinct.power.PrimalPowerReconciler;

import java.lang.reflect.Method;
import java.util.UUID;

/** Real server/mixin checks; run through tools/endgame-regression.init.gradle. */
public final class EndgameRegressionChecks implements ModInitializer {
    private boolean ran;
    private int checks;

    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran) return;
            ran = true;
            try {
                ServerWorld world = server.getOverworld();
                checkSscCompatibility(server);
                checkFeralPresentation(server);
                checkPortal(world);
                checkSpent(world);
                checkSlots(server);
                checkLanding(world);
                checkReturn(server);
                SSCPrimalstinct.LOGGER.info("ENDGAME_REGRESSION_PASSED: {} checks", checks);
            } catch (Throwable failure) {
                SSCPrimalstinct.LOGGER.error("ENDGAME_REGRESSION_FAILED", failure);
            } finally {
                server.stop(false);
            }
        });
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private void checkSscCompatibility(MinecraftServer server) throws Exception {
        check(net.onixary.sscPrimalstinct.data.PrimalRosterManager.active().revision > 0,
                "All addon powers and form profiles must load against current SSC");
        var player = player(server, "SscCompatibility");
        FormUtils.setForm(player, RegPlayerForms.OCELOT_3);
        check(FormUtils.getPlayerFormHistory(player).get(0) == RegPlayerForms.OCELOT_3,
                "Initial selection must update SSC form history");
        RegPrimalstinctComponent.PRIMALSTINCT.get(player).awakenPrimalInstinct();
        PrimalPowerReconciler.reconcile(player);
        var holder = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(player);
        var pounce = io.github.apace100.apoli.power.PowerTypeRegistry.get(
                new Identifier("shape-shifter-curse:form_ocelot_3_sneaking_jump_clash"));
        var replacement = io.github.apace100.apoli.power.PowerTypeRegistry.get(
                new Identifier("ssc-primalstinct:ocelot_3/common/sneaking_jump_clash_with_instinct"));
        var source = RegPlayerForms.OCELOT_3.getFormLayer().getRight();
        check(!holder.hasPower(pounce, source) && holder.hasPower(replacement), "Awakening replaces baseline pounce");
        var perk = net.onixary.shapeShifterCurseFabric.perk.RegPerks.P_OcelotArmor_1;
        net.onixary.shapeShifterCurseFabric.perk.PerkUtils.__addPerk(player,
                RegPlayerForms.OCELOT_3.getPerkTreeID(), perk);
        var field = PrimalPowerReconciler.class.getDeclaredField("PENDING");
        field.setAccessible(true);
        var pending = (java.util.Set<?>)field.get(null);
        pending.remove(player.getUuid());
        FormUtils.reApplyPower(player);
        check(holder.hasPower(pounce, source), "SSC rebuild restores baseline before addon reconciliation");
        // The queue resolves players through PlayerManager's UUID map.
        check(pending.contains(player.getUuid()), "Power rebuild queues addon reconciliation");
        PrimalPowerReconciler.reconcile(player);
        check(!holder.hasPower(pounce, source) && holder.hasPower(replacement), "Rebuild keeps only addon pounce");
        var armor = io.github.apace100.apoli.power.PowerTypeRegistry.get(new Identifier("shape-shifter-curse:perks/ocelot_armor_1"));
        check(holder.hasPower(armor, source), "Reconciliation preserves unlocked perks");
        var primal = RegPlayerForms.getPlayerForm(net.onixary.sscPrimalstinct.adapter.ssc.RegPrimalVariants.PRIMAL_OCELOT);
        check(primal.getPerkTreeID().equals(net.onixary.shapeShifterCurseFabric.perk.RegPerks.EMPTY_PERK_TREE), "Primal ocelot has no perk tree");
        FormUtils.setForm(player, primal);
        check(!holder.hasPower(armor), "Primal transformation does not inherit ordinary ocelot perks");
        net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter.rebuildCurrentForm(player);
        check(!holder.hasPower(armor), "Primal reload does not grant ordinary ocelot perks");
        var attuner = net.onixary.shapeShifterCurseFabric.blocks.RegCustomBlock.FORM_ATTUNER_BLOCK;
        var attunerPos = new BlockPos(12, 100, 12);
        var world = server.getOverworld();
        world.setBlockState(attunerPos, attuner.getDefaultState());
        var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(attunerPos), Direction.UP, attunerPos, false);
        attuner.onUse(attuner.getDefaultState(), world, attunerPos, player, net.minecraft.util.Hand.MAIN_HAND, hit);
        check(net.onixary.shapeShifterCurseFabric.blocks.FormAttunerBlock.playerLastAttunerPos.get(player) == null,
                "Primal attuner use must be blocked before recording attunement");
        FormUtils.setForm(player, RegPlayerForms.OCELOT_3);
        check(holder.hasPower(armor, source), "Ordinary ocelot perk progress remains intact");
        check(!net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter.blockPrimalAttunement(player),
                "Ordinary ocelot may still open perks");
        attuner.onUse(attuner.getDefaultState(), world, attunerPos, player, net.minecraft.util.Hand.MAIN_HAND, hit);
        check(attunerPos.equals(net.onixary.shapeShifterCurseFabric.blocks.FormAttunerBlock.playerLastAttunerPos.get(player)),
                "Ordinary ocelot may still use the attuner");
    }

    private static Method method(Class<?> type, String name, Class<?>... params) throws Exception {
        Method method = type.getDeclaredMethod(name, params);
        method.setAccessible(true);
        return method;
    }

    private static float baseScale(net.minecraft.entity.player.PlayerEntity player, String type) throws Exception {
        Object scaleType = Class.forName("virtuoel.pehkui.api.ScaleTypes").getField(type).get(null);
        Object data = scaleType.getClass().getMethod("getScaleData", net.minecraft.entity.Entity.class).invoke(scaleType, player);
        return (float) data.getClass().getMethod("getBaseScale").invoke(data);
    }

    private void checkFeralPresentation(MinecraftServer server) throws Exception {
        var player = player(server, "FeralAppearance");
        var world = server.getOverworld();
        var properties = (net.minecraft.world.level.ServerWorldProperties) world.getLevelProperties();
        long previousTime = world.getTime();
        try {
            for (var form : new net.onixary.shapeShifterCurseFabric.player_form.IForm[]{RegPlayerForms.AXOLOTL_3, RegPlayerForms.BAT_3}) {
                FormUtils.setForm(player, form);
                var holder = io.github.apace100.apoli.component.PowerHolderComponent.KEY.get(player);
                var powers = java.util.List.copyOf(holder.getPowers());
                var history = java.util.List.copyOf(FormUtils.getPlayerFormHistory(player));
                var tree = net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent.COMPONENT.get(player).nowPerkTree;
                var configuredProfile = net.onixary.sscPrimalstinct.feral.FeralProfiles.get(form.getFormID());
                float originalWidth = baseScale(player, "WIDTH");
                float originalEye = baseScale(player, "EYE_HEIGHT");
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(RegPrimalstinctItems.FERAL_CRYSTAL, 2));
                RegPrimalstinctItems.FERAL_CRYSTAL.use(world, player, net.minecraft.util.Hand.MAIN_HAND);
                var state = net.onixary.sscPrimalstinct.feral.RegFeralComponent.FERAL.get(player);
                check(state.transitioning() && !state.active(), "Whitelisted form begins cosmetic transformation");
                check(player.getMainHandStack().getCount() == 1, "Crystal consumes exactly one item");
                check(!net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Repeated use cannot restart an in-flight transformation");
                properties.setTime(world.getTime() + net.onixary.sscPrimalstinct.feral.FeralService.MIDPOINT);
                net.onixary.sscPrimalstinct.feral.FeralService.tick(player);
                var render = net.onixary.shapeShifterCurseFabric.util.FormTextureUtils.getPlayerForm_Render(player);
                check(state.active() && render.getBodyType() == net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType.FERAL,
                        "Render entry changes body type for mouth items and animations");
                check(FormUtils.getPlayerForm(player) == form && form.getBodyType() != render.getBodyType(), "Real form remains untouched");
                check(render.getFormID().equals(form.getFormID()) && render.getRenderLayerOverride().getRight().equals(configuredProfile.modelOrigin() != null ? configuredProfile.modelOrigin() : form.getFormLayer().getRight()),
                        "Presentation selects configured model origin and retains real form identity");
                check(render.getDefaultEyeScale() == configuredProfile.eyeScale() && render.getPerkTreeID().equals(tree), "Presentation uses configured eye baseline and preserves perk identity");
                check(baseScale(player, "WIDTH") == configuredProfile.scale() && baseScale(player, "HEIGHT") == configuredProfile.scale()
                        && baseScale(player, "EYE_HEIGHT") == configuredProfile.eyeScale() && baseScale(player, "HITBOX_HEIGHT") == configuredProfile.eyeScale(),
                        "Midpoint applies default body, eye and hitbox scales");
                var customProfile = new net.onixary.sscPrimalstinct.feral.FeralProfiles.Profile(null, null, 0.8f, 0.5f, configuredProfile.removePowers());
                state.setProfile(customProfile);
                state.ensureScale();
                var crawlType = io.github.apace100.apoli.power.PowerTypeRegistry.get(new Identifier("shape-shifter-curse:form_axolotl_3_crawling"));
                if (form == RegPlayerForms.AXOLOTL_3) {
                    check(!holder.hasPower(crawlType), "Feral axolotl removes crawling from its original source");
                    for (int tick = 0; tick < 5; tick++) {
                        holder.getPowers(net.onixary.shapeShifterCurseFabric.additional_power.CrawlingPower.class, true).forEach(p -> p.tick());
                    }
                    check(baseScale(player, "EYE_HEIGHT") == 0.5f, "Removed crawling cannot overwrite eye scale");
                }
                check(baseScale(player, "WIDTH") == 0.8f && baseScale(player, "EYE_HEIGHT") == 0.5f
                        && state.renderForm(form).getDefaultEyeScale() == 0.5f, "Changed profile updates physical scale and render eye baseline together");
                state.setProfile(net.onixary.sscPrimalstinct.feral.FeralProfiles.get(form.getFormID()));
                state.ensureScale();
                var customRender = new net.onixary.sscPrimalstinct.feral.FeralPresentation(form,
                        new net.onixary.sscPrimalstinct.feral.FeralProfiles.Profile(RegPlayerForms.OCELOT_3.getFormID(), RegPlayerForms.OCELOT_3.getFormID()));
                check(customRender.getRenderLayerOverride().getRight().equals(RegPlayerForms.OCELOT_3.getFormLayer().getRight()),
                        "Configured replacement selects the donor model");
                var animData = new net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem.AnimSystemData(player);
                var idle = net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimRegistries.ANIM_STATE_IDLE;
                check(customRender.getAnimStateController(player, animData, idle) == RegPlayerForms.OCELOT_3.getAnimStateController(player, animData, idle),
                        "Configured replacement selects the donor animation controller");
                check(render.getAnimStateController(player, animData, idle) == net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase.IDLE_CONTROLLER,
                        "Default animation uses the common feral controller");
                check(java.util.List.copyOf(holder.getPowers()).equals(powers.stream().filter(p -> !configuredProfile.removePowers().contains(p.getType().getIdentifier())).toList()) && FormUtils.getPlayerFormHistory(player).equals(history),
                        "Only configured powers are removed; other instances and form history remain unchanged");
                var saved = new NbtCompound();
                state.writeToNbt(saved);
                var reloaded = new net.onixary.sscPrimalstinct.feral.FeralComponent(player);
                reloaded.readFromNbt(saved);
                check(reloaded.active() && !reloaded.transitioning(), "Active appearance persists without resuming an interrupted animation");
                check(reloaded.profile().equals(state.profile()), "Scale configuration persists through NBT");
                var packet = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                try {
                    state.writeSyncPacket(packet, player);
                    reloaded.applySyncPacket(packet);
                    check(reloaded.active() && reloaded.transitioning(), "Tracking sync includes appearance and animation state");
                    check(reloaded.profile().equals(state.profile()), "Tracking sync includes scale configuration");
                } finally { packet.release(); }
                properties.setTime(world.getTime() + net.onixary.sscPrimalstinct.feral.FeralService.DURATION);
                net.onixary.sscPrimalstinct.feral.FeralService.tick(player);
                check(!state.transitioning() && state.active(), "Transformation completes in feral presentation");
                FormUtils._loadForm(player, form, false);
                check(state.active(), "Same-form reload retains feral presentation");
                check(baseScale(player, "EYE_HEIGHT") == configuredProfile.eyeScale(), "Same-form reload reapplies feral scale");
                FormUtils.reApplyPower(player);
                check(baseScale(player, "EYE_HEIGHT") == configuredProfile.eyeScale(), "Power rebuild cannot reset feral eye scale");
                if (form == RegPlayerForms.AXOLOTL_3) check(!holder.hasPower(crawlType), "Power rebuild re-applies configured masks");
                powers = java.util.List.copyOf(holder.getPowers());
                check(!net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Already feral appearance rejects another crystal");
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(RegPrimalstinctItems.RESTORATION_DUST, 2));
                RegPrimalstinctItems.RESTORATION_DUST.use(world, player, net.minecraft.util.Hand.MAIN_HAND);
                check(state.restoring() && state.transitioning() && player.getMainHandStack().getCount() == 1, "Restoration begins and consumes one dust");
                properties.setTime(world.getTime() + net.onixary.sscPrimalstinct.feral.FeralService.DURATION);
                net.onixary.sscPrimalstinct.feral.FeralService.tick(player);
                check(!state.active() && state.source() == null && state.renderForm(form) == form, "Restoration returns the original presentation");
                check(baseScale(player, "WIDTH") == originalWidth && baseScale(player, "EYE_HEIGHT") == originalEye,
                        "Restoration reapplies original form scales");
                holder.getPowers(net.onixary.shapeShifterCurseFabric.additional_power.CrawlingPower.class, true).forEach(p -> p.tick());
                if (form == RegPlayerForms.AXOLOTL_3) check(holder.hasPower(crawlType, form.getFormLayer().getRight()), "Restoration returns crawling to original source");
                check(baseScale(player, "EYE_HEIGHT") == originalEye, "Crawling ticks after restoration use original baseline");
                check(holder.getPowers().containsAll(powers), "Restoration preserves all unmasked power instances");
                check(!net.onixary.sscPrimalstinct.feral.FeralService.start(player, true), "Dust rejects an unaffected player");
                if (form == RegPlayerForms.AXOLOTL_3) {
                    var origin = form.getFormLayer().getRight();
                    var otherSource = new Identifier("ssc-primalstinct", "regression_other_source");
                    holder.addPower(crawlType, otherSource);
                    var sharedInstance = holder.getPower(crawlType);
                    state.start(form.getFormID(), configuredProfile, false);
                    state.applyMidpoint();
                    check(!holder.hasPower(crawlType, origin) && holder.hasPower(crawlType, otherSource)
                            && holder.getPower(crawlType) == sharedInstance, "Masks preserve another source's shared power instance");
                    state.clear();
                    check(holder.hasPower(crawlType, origin) && holder.hasPower(crawlType, otherSource), "Restoration adds back only the original source");
                    holder.removePower(crawlType, otherSource);
                    state.start(form.getFormID(), configuredProfile, false);
                    state.applyMidpoint();
                    var maskSave = new NbtCompound();
                    state.writeToNbt(maskSave);
                    state.readFromNbt(maskSave);
                    state.setProfile(new net.onixary.sscPrimalstinct.feral.FeralProfiles.Profile(null, null));
                    state.ensureScale();
                    check(holder.hasPower(crawlType, origin), "Persisted mask records restore powers when configuration removes the mask");
                    state.setProfile(configuredProfile);
                    state.ensureScale();
                    check(!holder.hasPower(crawlType, origin), "Adding a mask in configuration removes the power again");
                    state.clear();
                    check(holder.hasPower(crawlType, origin), "Clearing feral presentation restores saved masks");
                }
                check(net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Crystal can be applied again after restoration");
                state.applyMidpoint();
                FormUtils.setForm(player, RegPlayerForms.OCELOT_3);
                check(state.source() == null && !state.transitioning(), "Actual form change cancels cosmetic transformation");
                check(!holder.hasPower(crawlType), "Changing forms does not grant the previous form's masked crawling power");
                check(!net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Native feral form is ineligible");
            }
            // The actual subform ID is retained by SSC, despite inheriting its master's powers.
            FormUtils.setForm(player, RegPlayerForms.BAT_3);
            check(net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Main bat form is eligible");
            var state = net.onixary.sscPrimalstinct.feral.RegFeralComponent.FERAL.get(player);
            state.applyMidpoint();
            FormUtils.setForm(player, RegPlayerForms.BAT_3_SUB_AVALI);
            check(state.source() == null && !state.active(), "Switching to a subform clears active feral presentation");
            check(!net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Subform does not inherit its master's whitelist entry");
            // Simulate a datapack explicitly whitelisting a subform: it must still be rejected.
            var profilesField = net.onixary.sscPrimalstinct.feral.FeralProfiles.class.getDeclaredField("profiles");
            profilesField.setAccessible(true);
            Object previousProfiles = profilesField.get(null);
            try {
                var sub = RegPlayerForms.BAT_3_SUB_AVALI;
                var profile = new net.onixary.sscPrimalstinct.feral.FeralProfiles.Profile(null, null);
                profilesField.set(null, java.util.Map.of(sub.getFormID(), profile));
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(RegPrimalstinctItems.FERAL_CRYSTAL, 2));
                RegPrimalstinctItems.FERAL_CRYSTAL.use(world, player, net.minecraft.util.Hand.MAIN_HAND);
                check(state.source() == null && player.getMainHandStack().getCount() == 2,
                        "Even an explicitly whitelisted subform rejects the crystal without consuming it");
                state.start(sub.getFormID(), profile, false);
                state.applyMidpoint();
                check(state.renderForm(sub) == sub, "Stale or synced subform state cannot replace its presentation");
                net.onixary.sscPrimalstinct.feral.FeralService.tick(player);
                check(state.source() == null && !state.active(), "Tick clears stale subform state even with a matching whitelist entry");
            } finally {
                profilesField.set(null, previousProfiles);
            }
            FormUtils.setForm(player, RegPlayerForms.BAT_3);
            check(!state.active(), "Returning from a subform does not reactivate old feral state");
            FormUtils.setForm(player, RegPlayerForms.BAT_2);
            check(!net.onixary.sscPrimalstinct.feral.FeralService.start(player, false), "Non-permanent form is ineligible");
            for (String item : new String[]{"feral_crystal", "restoration_dust"}) {
                var recipe = server.getRecipeManager().get(new Identifier("ssc-primalstinct", "altar/" + item)).orElseThrow();
                check(recipe instanceof net.onixary.shapeShifterCurseFabric.recipes.altar.AltarRecipe, "Recipe loads as an SSC altar recipe");
                var altarRecipe = (net.onixary.shapeShifterCurseFabric.recipes.altar.AltarRecipe) recipe;
                check(altarRecipe.totalFuelUsage() == 4 * 800, "Recipe consumes exactly four moondust fuel units");
                check(recipe.getIngredients().size() == 3 && recipe.getOutput(server.getRegistryManager()).getCount() == 1,
                        "Recipe has three ingredients and one output");
            }
        } finally {
            properties.setTime(previousTime);
        }
    }

    private void checkPortal(ServerWorld world) throws Exception {
        BlockPos origin = new BlockPos(0, 100, 0);
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 3; z++) {
            boolean border = x == -1 || x == 3 || z == -1 || z == 3;
            boolean corner = (x == -1 || x == 3) && (z == -1 || z == 3);
            world.setBlockState(origin.add(x, 0, z), border && !corner
                    ? RegEndgameBlocks.PRIMAL_PORTAL_FRAME.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        var state = EndgameWorldState.get(world);
        state.setPortalState(world, origin.add(1, 0, 1), EndgameWorldState.PortalState.CLOSED);
        var fragments = new ItemEntity(world, 1.5, 100.5, 1.5, new ItemStack(RegPrimalstinctItems.SEDATIVE_FRAGMENT, 5));
        Method open = method(RitualPortalService.class, "tryOpen", ServerWorld.class, ItemEntity.class, BlockPos.class);
        world.setBlockState(origin, Blocks.BEDROCK.getDefaultState());
        check(!(boolean)open.invoke(null, world, fragments, origin), "Solid hole must reject opening");
        check(world.getBlockState(origin).isOf(Blocks.BEDROCK) && fragments.getStack().getCount() == 5,
                "Rejected opening must preserve blocks and fragments");
        world.setBlockState(origin, Blocks.OAK_SLAB.getDefaultState().with(Properties.WATERLOGGED, true));
        check(!(boolean)open.invoke(null, world, fragments, origin), "Waterlogged solids are not liquid holes");
        world.setBlockState(origin, Blocks.WATER.getDefaultState());
        check((boolean)open.invoke(null, world, fragments, origin), "Air/liquid hole must open");
        check(fragments.getStack().getCount() == 4 && world.getBlockState(origin).isOf(RegEndgameBlocks.PRIMAL_PORTAL),
                "Opening consumes exactly one fragment");
        check(!(boolean)open.invoke(null, world, fragments, origin) && fragments.getStack().getCount() == 4,
                "Already open portal must not consume again");
        BlockPos frame = origin.add(-1, 0, 0);
        world.setBlockState(frame, Blocks.AIR.getDefaultState());
        world.setBlockState(frame, RegEndgameBlocks.PRIMAL_PORTAL_FRAME.getDefaultState());
        check((boolean)open.invoke(null, world, fragments, origin) && fragments.getStack().getCount() == 3,
                "Repaired broken portal must reopen with a new fragment");
        state.setPortalState(world, origin.add(1, 0, 1), EndgameWorldState.PortalState.CLOSED);
        check(!(boolean)open.invoke(null, world, fragments, origin), "Existing portal blocks reject opening even without saved OPEN state");
    }

    private void checkSpent(ServerWorld world) throws Exception {
        var config = EndgameRosterManager.active();
        BlockPos altar = new BlockPos(-8, 100, 24);
        BlockPos locate = new BlockPos(-16, 0, 16);
        var state = new EndgameWorldState();
        check(state.markRewardClaimed(world, altar, config.ritualId), "First claim succeeds");
        check(!state.markRewardClaimed(world, altar, config.ritualId), "Duplicate claim rejected");
        check(state.isRewardClaimedInChunk(world, locate, config.ritualId), "Locate coordinates find the claimed altar");
        check(!state.isRewardClaimedInChunk(world, locate.add(16, 0, 0), config.ritualId), "Neighboring structure remains available");
        check(!state.isRewardClaimedInChunk(world, locate, new Identifier("test:other")), "Ritual IDs remain isolated");
        // Old-format saves have only the exact-position key; no new index field is serialized.
        var reloaded = EndgameWorldState.fromNbt(state.writeNbt(new NbtCompound()));
        check(reloaded.isRewardClaimedInChunk(world, locate, config.ritualId), "Old save records rebuild navigation index");
        check(reloaded.isRewardClaimed(world, altar, config.ritualId), "Exact claim record survives reload");
        EndgameWorldState.get(world).markRewardClaimed(world, altar, config.ritualId);
        check((boolean)method(RemnantService.class, "isSpent", ServerWorld.class, BlockPos.class, EndgameRitualConfig.class)
                .invoke(null, world, locate, config), "Remnant navigation uses the structure index");
    }

    private static ServerPlayerEntity player(MinecraftServer server, String name) {
        var player = new ServerPlayerEntity(server, server.getOverworld(), new GameProfile(UUID.randomUUID(), name));
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
        return player;
    }

    private void checkSlots(MinecraftServer server) {
        var restricted = player(server, "Restricted");
        FormUtils._loadForm(restricted, RegPlayerForms.OCELOT_3, true);
        var component = RegPrimalstinctComponent.PRIMALSTINCT.get(restricted);
        component.awakenPrimalInstinct();
        component.setValue(75);
        PrimalPowerReconciler.reconcile(restricted);
        check(InventoryLockManager.ruleIfRestricted(restricted).lockOffhand(), "Test player has locked offhand");
        restricted.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
        restricted.playerScreenHandler.onSlotClick(36, 40, SlotActionType.SWAP, restricted);
        check(restricted.getOffHandStack().isEmpty() && restricted.getInventory().getStack(0).isOf(Items.DIAMOND),
                "F-key swap cannot bypass offhand lock");
        restricted.playerScreenHandler.onSlotClick(36, 8, SlotActionType.SWAP, restricted);
        check(restricted.getInventory().getStack(8).isEmpty(), "Number-key swap cannot bypass hotbar lock");
        restricted.playerScreenHandler.onSlotClick(36, 1, SlotActionType.SWAP, restricted);
        check(restricted.getInventory().getStack(1).isOf(Items.DIAMOND), "Unlocked hotbar swaps still work");
        var unrestricted = player(server, "Unrestricted");
        unrestricted.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
        unrestricted.playerScreenHandler.onSlotClick(36, 40, SlotActionType.SWAP, unrestricted);
        check(unrestricted.getOffHandStack().isOf(Items.DIAMOND), "Other players retain normal offhand swaps");
    }

    private void checkLanding(ServerWorld world) throws Exception {
        Method safe = method(EndgameTeleportService.class, "safeLanding", ServerWorld.class, BlockPos.class);
        BlockPos pos = new BlockPos(24, 100, 24);
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
        for (var floor : new Block[]{Blocks.AIR, Blocks.LAVA, Blocks.WATER, Blocks.MAGMA_BLOCK, Blocks.CACTUS, Blocks.POWDER_SNOW}) {
            world.setBlockState(pos.down(), floor.getDefaultState());
            check(safe.invoke(null, world, pos) == null, "Reject unsafe support: " + floor);
        }
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        check(safe.invoke(null, world, pos) != null, "Solid floor with headroom is safe");
        world.setBlockState(pos.up(), Blocks.LAVA.getDefaultState());
        check(safe.invoke(null, world, pos) == null, "Reject dangerous fluid at head height");
        world.setBlockState(pos.up(), Blocks.STONE.getDefaultState());
        check(safe.invoke(null, world, pos) == null, "Reject blocked headroom");
        world.setBlockState(pos.up(), Blocks.AIR.getDefaultState());
        world.setBlockState(pos.down(), Blocks.STONE_SLAB.getDefaultState());
        Vec3d slabLanding = (Vec3d)method(EndgameTeleportService.class, "findSafeLandingNear", ServerWorld.class, BlockPos.class)
                .invoke(null, world, pos);
        check(slabLanding != null && slabLanding.y == 99.5, "Preserve half-slab landing height");
    }

    private static void platform(ServerWorld world, BlockPos center) {
        for (BlockPos pos : BlockPos.iterate(center.add(-3, -1, -3), center.add(3, 3, 3))) {
            world.setBlockState(pos, pos.getY() == center.getY() - 1
                    ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private void checkReturn(MinecraftServer server) throws Exception {
        ServerWorld world = server.getOverworld();
        BlockPos blocked = new BlockPos(0, 140, 32);
        for (BlockPos pos : BlockPos.iterate(blocked.add(-8, -4, -8), blocked.add(8, 4, 8))) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        check(method(EndgameTeleportService.class, "findSafeLandingNear", ServerWorld.class, BlockPos.class)
                .invoke(null, world, blocked) == null, "Exhausted search must return no landing");
        Method goBack = method(EndgameTeleportService.class, "tryReturn", ServerPlayerEntity.class);
        var a = player(server, "ReturnBed");
        BlockPos bed = new BlockPos(48, 110, 0);
        platform(world, bed);
        var bedState = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST);
        world.setBlockState(bed.west(), bedState.with(BedBlock.PART, BedPart.FOOT), Block.NOTIFY_LISTENERS);
        world.setBlockState(bed, bedState.with(BedBlock.PART, BedPart.HEAD), Block.NOTIFY_LISTENERS);
        a.setSpawnPoint(World.OVERWORLD, bed, 90, false, false);
        RegEndgameComponent.ENDGAME.get(a).setReturnAnchor(new EndgamePlayerComponent.ReturnAnchor(
                "minecraft:overworld", blocked.getX(), blocked.getY(), blocked.getZ(), 0, 0));
        goBack.invoke(null, a);
        check(a.getServerWorld() == world && a.getBlockPos().isWithinDistance(bed, 3), "Blocked return goes to player's bed");

        var b = player(server, "ReturnAnchor");
        ServerWorld nether = server.getWorld(World.NETHER);
        BlockPos respawnAnchor = new BlockPos(48, 110, 0);
        platform(nether, respawnAnchor);
        nether.setBlockState(respawnAnchor, Blocks.RESPAWN_ANCHOR.getDefaultState().with(RespawnAnchorBlock.CHARGES, 2));
        b.setSpawnPoint(World.NETHER, respawnAnchor, 0, false, false);
        RegEndgameComponent.ENDGAME.get(b).setReturnAnchor(RegEndgameComponent.ENDGAME.get(a).getReturnAnchor());
        goBack.invoke(null, b);
        check(b.getServerWorld() == nether && b.getBlockPos().isWithinDistance(respawnAnchor, 3), "Each player uses their own respawn dimension");
        check(nether.getBlockState(respawnAnchor).get(RespawnAnchorBlock.CHARGES) == 2, "Returning does not spend respawn anchor charges");

        world.setBlockState(bed, Blocks.AIR.getDefaultState());
        goBack.invoke(null, a);
        check(a.getServerWorld() == world && a.getBlockPos().isWithinDistance(world.getSpawnPos(), 32), "Destroyed bed falls back to world spawn");
        BlockPos normal = new BlockPos(64, 110, 32);
        platform(world, normal);
        RegEndgameComponent.ENDGAME.get(a).setReturnAnchor(new EndgamePlayerComponent.ReturnAnchor(
                "minecraft:overworld", normal.getX(), normal.getY(), normal.getZ(), 45, 0));
        goBack.invoke(null, a);
        check(a.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(normal)) < 0.001, "Valid original return anchor stays preferred");
    }
}
