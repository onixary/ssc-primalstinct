package net.onixary.sscPrimalstinct.feral;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.ISubForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import java.util.Objects;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/** Active appearance survives saves; in-flight visuals are not resumed after reconnect/death. */
public final class FeralComponent implements AutoSyncedComponent {
    private final PlayerEntity player;
    private Identifier source;
    private FeralProfiles.Profile profile = new FeralProfiles.Profile(null, null);
    private boolean active;
    private boolean restoring;
    private long transitionStart = -1;
    private FeralPresentation cached;
    private IForm cachedOriginal;
    private FeralProfiles.Profile appliedScale;
    private final java.util.Map<Identifier, NbtElement> removedPowers = new java.util.HashMap<>();

    /** Like PrimalPowerReconciler, only mask the real form's origin source. */
    public void reconcilePowers() {
        if (player.getWorld().isClient() || source == null) return;
        var form = FormUtils.getPlayerForm(player);
        if (!form.getFormID().equals(source)) {
            removedPowers.clear(); // SSC rebuilds the new form; never grant old powers to it.
            return;
        }
        var holder = PowerHolderComponent.KEY.get(player);
        var origin = form.getFormLayer().getRight();
        var desired = active ? profile.removePowers() : java.util.Set.<Identifier>of();
        boolean changed = false;
        for (var id : java.util.Set.copyOf(removedPowers.keySet())) {
            if (desired.contains(id)) continue;
            if (PowerTypeRegistry.contains(id)) {
                var type = PowerTypeRegistry.get(id);
                boolean existed = holder.hasPower(type);
                changed |= holder.addPower(type, origin);
                if (!existed && holder.getPower(type) != null) holder.getPower(type).fromTag(removedPowers.get(id));
            }
            removedPowers.remove(id);
        }
        for (var id : desired) {
            if (!PowerTypeRegistry.contains(id)) continue;
            var type = PowerTypeRegistry.get(id);
            if (holder.hasPower(type, origin)) {
                removedPowers.putIfAbsent(id, holder.getPower(type).toTag().copy());
                holder.removePower(type, origin);
                changed = true;
            }
        }
        if (changed) PowerHolderComponent.KEY.sync(player);
    }

    public void refreshScale() {
        if (player.getWorld().isClient()) return;
        reconcilePowers();
        var form = FormUtils.getPlayerForm(player);
        var rendered = renderForm(form);
        if (rendered != form) {
            rendered.applyScale(player);
            appliedScale = profile;
        }
    }

    public void ensureScale() {
        reconcilePowers();
        if (active && !Objects.equals(appliedScale, profile)) refreshScale();
    }

    private void restoreScale() {
        if (!player.getWorld().isClient()) FormUtils.getPlayerForm(player).applyScale(player);
        appliedScale = null;
    }

    public FeralComponent(PlayerEntity player) { this.player = player; }
    public Identifier source() { return source; }
    public boolean active() { return active; }
    public boolean transitioning() { return transitionStart >= 0; }
    public boolean restoring() { return restoring; }
    public long elapsed() { return player.getWorld().getTime() - transitionStart; }
    public void sync() { RegFeralComponent.FERAL.sync(player); }

    public void start(Identifier form, FeralProfiles.Profile profile, boolean restore) {
        source = form;
        setProfile(profile);
        restoring = restore;
        transitionStart = player.getWorld().getTime();
        sync();
    }
    public void applyMidpoint() {
        active = !restoring;
        reconcilePowers();
        if (active) refreshScale(); else restoreScale();
        sync();
        if (!active) resetFirstPersonOffset();
    }
    public void finish() {
        transitionStart = -1;
        if (!active) source = null;
        sync();
    }
    public void clear() {
        boolean wasActive = active;
        if (active || appliedScale != null) restoreScale();
        active = false;
        reconcilePowers();
        source = null;
        active = false;
        transitionStart = -1;
        cached = null;
        sync();
        if (wasActive) resetFirstPersonOffset();
    }
    private void resetFirstPersonOffset() {
        // Send after the appearance sync so Feral rendering no longer reinstates its offset.
        if (!player.getWorld().isClient()) TransformManager.sendClientFirstPersonReset(player);
    }
    public void setProfile(FeralProfiles.Profile next) {
        if (!Objects.equals(profile, next)) { profile = next; cached = null; }
    }
    public FeralProfiles.Profile profile() { return profile; }
    public IForm renderForm(IForm original) {
        if (!active || !original.getFormID().equals(source)
                || original instanceof ISubForm sub && sub.isSubForm()) return original;
        if (cached == null || cachedOriginal != original) {
            cachedOriginal = original;
            cached = new FeralPresentation(original, profile);
        }
        return cached;
    }
    @Override public void writeToNbt(NbtCompound tag) {
        if (source != null) tag.putString("source", source.toString());
        tag.putBoolean("active", active);
        tag.putFloat("scale", profile.scale());
        tag.putFloat("eye_scale", profile.eyeScale());
        if (profile.modelOrigin() != null) tag.putString("model_origin", profile.modelOrigin().toString());
        var configured = new NbtList();
        for (var id : profile.removePowers()) configured.add(NbtString.of(id.toString()));
        tag.put("remove_powers", configured);
        var removed = new NbtList();
        removedPowers.forEach((id, data) -> {
            var entry = new NbtCompound();
            entry.putString("id", id.toString());
            entry.put("data", data.copy());
            removed.add(entry);
        });
        tag.put("removed_powers", removed);
        if (profile.modelForm() != null) tag.putString("model", profile.modelForm().toString());
        if (profile.animationForm() != null) tag.putString("animation", profile.animationForm().toString());
    }
    @Override public void readFromNbt(NbtCompound tag) {
        source = tag.contains("source") ? Identifier.tryParse(tag.getString("source")) : null;
        active = source != null && tag.getBoolean("active");
        var configured = new java.util.HashSet<Identifier>();
        for (var entry : tag.getList("remove_powers", NbtElement.STRING_TYPE)) configured.add(new Identifier(entry.asString()));
        removedPowers.clear();
        for (var entry : tag.getList("removed_powers", NbtElement.COMPOUND_TYPE)) {
            var removed = (NbtCompound) entry;
            if (removed.contains("data")) removedPowers.put(new Identifier(removed.getString("id")), removed.get("data").copy());
        }
        profile = new FeralProfiles.Profile(
                tag.contains("model") ? Identifier.tryParse(tag.getString("model")) : null,
                tag.contains("animation") ? Identifier.tryParse(tag.getString("animation")) : null,
                tag.contains("scale") ? tag.getFloat("scale") : 1.0f,
                tag.contains("eye_scale") ? tag.getFloat("eye_scale") : 0.6f, configured,
                tag.contains("model_origin") ? Identifier.tryParse(tag.getString("model_origin")) : null);
        appliedScale = null;
        transitionStart = -1;
        cached = null;
    }
    @Override public void writeSyncPacket(net.minecraft.network.PacketByteBuf buf, net.minecraft.server.network.ServerPlayerEntity recipient) {
        var tag = new NbtCompound();
        writeToNbt(tag);
        tag.putLong("transition_start", transitionStart);
        tag.putBoolean("restoring", restoring);
        buf.writeNbt(tag);
    }
    @Override public void applySyncPacket(net.minecraft.network.PacketByteBuf buf) {
        NbtCompound tag = buf.readNbt();
        if (tag == null) return;
        readFromNbt(tag);
        transitionStart = tag.getLong("transition_start");
        restoring = tag.getBoolean("restoring");
    }
}
