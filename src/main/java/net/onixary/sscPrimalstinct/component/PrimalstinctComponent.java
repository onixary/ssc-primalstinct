package net.onixary.sscPrimalstinct.component;

import dev.onyxstudios.cca.api.v3.component.Component;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 卡03：玩家持久状态，新本能的唯一权威数据源。
 * 持久字段：schemaVersion/selectionCompleted/selectedFormId/value/locked；
 * level 由服务端 value 与等级表派生（不另存）；rate、活跃 Power 来源、会话 nonce、UI 状态
 * 均为运行期数据不落盘。读 NBT 不做库存搬移或 grant power（留给主线程恢复阶段，卡06/09）。
 * 死亡/重登/换维不重置：CCA RespawnCopyStrategy.ALWAYS_COPY + 换维组件随实体持久。
 */
public class PrimalstinctComponent implements Component {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final PlayerEntity player;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean selectionCompleted = false;
    private @Nullable Identifier selectedFormId = null;
    private float value = 0.0f;
    private boolean locked = false;

    /** 卡09：锁槽暂存（按原槽索引记录；-1 表示光标来源）。完整保存 NBT/数量。 */
    private final List<StashEntry> stash = new ArrayList<>();

    public record StashEntry(int slot, ItemStack stack) {
    }

    public PrimalstinctComponent(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity getPlayer() {
        return player;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public boolean isSelectionCompleted() {
        return selectionCompleted;
    }

    public void setSelectionCompleted(boolean selectionCompleted) {
        this.selectionCompleted = selectionCompleted;
    }

    public @Nullable Identifier getSelectedFormId() {
        return selectedFormId;
    }

    public void setSelectedFormId(@Nullable Identifier selectedFormId) {
        this.selectedFormId = selectedFormId;
    }

    /** 统一入口：非有限数按 0 处理并告警，范围钳制 [0, 等级表满值]。 */
    public float getValue() {
        return value;
    }

    public void setValue(float newValue) {
        if (!Float.isFinite(newValue)) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 拒绝非有限数值 {}（玩家 {}），按 0 处理",
                    newValue, player.getGameProfile().getName());
            newValue = 0.0f;
        }
        float max = PrimalRosterManager.active().levels.maxValue;
        this.value = Math.max(0.0f, Math.min(max, newValue));
    }

    /** 派生等级：由服务端 value 与运行名单的等级表计算，避免存档漂移。 */
    public int getLevel() {
        return PrimalRosterManager.active().levels.levelForValue(value);
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /** 读档后调用：按“满值即锁定”不变量修复（不缩小 value、不派发副作用）。 */
    public void repairInvariants() {
        float max = PrimalRosterManager.active().levels.maxValue;
        if (!Float.isFinite(value)) {
            value = 0.0f;
        }
        value = Math.max(0.0f, Math.min(max, value));
        if (value >= max) {
            locked = true;
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 0;
        selectionCompleted = tag.getBoolean("selectionCompleted");
        String selected = tag.contains("selectedFormId") ? tag.getString("selectedFormId") : "";
        selectedFormId = selected.isEmpty() ? null : Identifier.tryParse(selected);
        value = tag.getFloat("value");
        if (!Float.isFinite(value)) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 存档数值非有限（{}），重置为 0", value);
            value = 0.0f;
        }
        locked = tag.getBoolean("locked");
        stash.clear();
        NbtList stashList = tag.getList("stashSlots", 10);
        for (int i = 0; i < stashList.size(); i++) {
            NbtCompound entry = stashList.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(entry.getCompound("item"));
            if (!stack.isEmpty()) {
                stash.add(new StashEntry(entry.getInt("slot"), stack));
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        tag.putInt("schemaVersion", schemaVersion);
        tag.putBoolean("selectionCompleted", selectionCompleted);
        if (selectedFormId != null) {
            tag.putString("selectedFormId", selectedFormId.toString());
        }
        tag.putFloat("value", value);
        tag.putBoolean("locked", locked);
        if (!stash.isEmpty()) {
            NbtList stashList = new NbtList();
            for (StashEntry entry : stash) {
                NbtCompound compound = new NbtCompound();
                compound.putInt("slot", entry.slot());
                NbtCompound item = new NbtCompound();
                entry.stack().writeNbt(item);
                compound.put("item", item);
                stashList.add(compound);
            }
            tag.put("stashSlots", stashList);
        }
    }

    public List<StashEntry> getStash() {
        return stash;
    }

    public void stashItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (StashEntry entry : stash) {
            if (entry.slot() == slot && ItemStack.canCombine(entry.stack(), stack)) {
                long max = Math.min(entry.stack().getMaxCount(), Integer.MAX_VALUE);
                long total = (long) entry.stack().getCount() + stack.getCount();
                if (total <= max) {
                    entry.stack().setCount((int) total);
                    return;
                }
            }
        }
        stash.add(new StashEntry(slot, stack));
    }

    public void removeStashEntry(StashEntry entry) {
        stash.remove(entry);
    }

    public void clearStash() {
        stash.clear();
    }
}
