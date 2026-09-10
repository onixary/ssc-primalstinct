package net.onixary.sscPrimalstinct.endgame.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 眷属实现02：终局世界状态（PersistentState，按世界保存）。
 * 祭坛失效等“BE 被替换后仍需保留的事实”记在这里（SPENT 不因方块替换而丢失）。
 * 键：维度+祭坛坐标+ritualId → 实例记录（奖励是否已领、门户状态）。
 * 场景初始化（固定化身维度）单独记 templateVersion 与完成标记（眷属实现09）。
 */
public class EndgameWorldState extends PersistentState {

    private static final String KEY = SSCPrimalstinct.MOD_ID + "_endgame";

    /** 门户状态：CLOSED→OPEN→BROKEN（眷属实现02 不变量）。 */
    public enum PortalState {
        CLOSED, OPEN, BROKEN
    }

    /** 单个原初祭坛实例的持久事实。 */
    public static final class AltarInstance {
        public boolean rewardClaimed;
        public PortalState portal = PortalState.CLOSED;
    }

    private final Map<String, AltarInstance> altars = new HashMap<>();
    /** 门户状态（眷属实现08）：键 = 维度|内孔中心坐标；BROKEN 后修框需新碎片再开。 */
    private final Map<String, PortalState> portals = new HashMap<>();
    /** 化身维度场景初始化：templateVersion 与完成标记（幂等初始化，眷属实现09）。 */
    private int sceneTemplateVersion = -1;
    private boolean sceneInitialized = false;

    public static EndgameWorldState get(net.minecraft.server.world.ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(EndgameWorldState::fromNbt,
                EndgameWorldState::new, KEY);
    }

    public static EndgameWorldState fromNbt(NbtCompound tag) {
        EndgameWorldState state = new EndgameWorldState();
        state.sceneTemplateVersion = tag.contains("sceneTemplateVersion") ? tag.getInt("sceneTemplateVersion") : -1;
        state.sceneInitialized = tag.getBoolean("sceneInitialized");
        state.altars.clear();
        NbtCompound altarsTag = tag.getCompound("altars");
        for (String key : altarsTag.getKeys()) {
            NbtCompound instance = altarsTag.getCompound(key);
            AltarInstance altar = new AltarInstance();
            altar.rewardClaimed = instance.getBoolean("rewardClaimed");
            try {
                altar.portal = PortalState.valueOf(instance.getString("portal"));
            } catch (IllegalArgumentException e) {
                altar.portal = PortalState.CLOSED;
            }
            state.altars.put(key, altar);
        }
        state.portals.clear();
        NbtCompound portalsTag = tag.getCompound("portals");
        for (String key : portalsTag.getKeys()) {
            try {
                state.portals.put(key, PortalState.valueOf(portalsTag.getString(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.putInt("sceneTemplateVersion", sceneTemplateVersion);
        tag.putBoolean("sceneInitialized", sceneInitialized);
        NbtCompound altarsTag = new NbtCompound();
        for (Map.Entry<String, AltarInstance> entry : altars.entrySet()) {
            NbtCompound instance = new NbtCompound();
            instance.putBoolean("rewardClaimed", entry.getValue().rewardClaimed);
            instance.putString("portal", entry.getValue().portal.name());
            altarsTag.put(entry.getKey(), instance);
        }
        tag.put("altars", altarsTag);
        NbtCompound portalsTag = new NbtCompound();
        for (Map.Entry<String, PortalState> entry : portals.entrySet()) {
            portalsTag.putString(entry.getKey(), entry.getValue().name());
        }
        tag.put("portals", portalsTag);
        return tag;
    }

    // ---------- 门户状态（按内孔中心，眷属实现08） ----------

    private static String portalKey(net.minecraft.world.World world, BlockPos center) {
        return world.getRegistryKey().getValue() + "|portal|" + center.asLong();
    }

    public PortalState portalStateCenter(net.minecraft.world.World world, BlockPos center) {
        return portals.getOrDefault(portalKey(world, center), PortalState.CLOSED);
    }

    public void setPortalState(net.minecraft.world.World world, BlockPos center, PortalState state) {
        portals.put(portalKey(world, center), state);
        markDirty();
    }

    /** 实例键：维度+坐标+ritualId（眷属实现02 的世界状态键约定）。 */
    public static String altarKey(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                  Identifier ritualId) {
        return world.getRegistryKey().getValue() + "|" + pos.asLong() + "|" + ritualId;
    }

    /** 取实例；不存在时创建（仅在祭坛方块生成时调用，避免为查询而造记录）。 */
    public AltarInstance altar(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                               Identifier ritualId) {
        return altars.computeIfAbsent(altarKey(world, pos, ritualId), k -> {
            markDirty();
            return new AltarInstance();
        });
    }

    public @Nullable AltarInstance peekAltar(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                             Identifier ritualId) {
        return altars.get(altarKey(world, pos, ritualId));
    }

    /** 标记奖励已领（SPENT 事实），并返回是否首次（false=重复请求，需拒绝）。 */
    public boolean markRewardClaimed(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                     Identifier ritualId) {
        AltarInstance instance = altar(world, pos, ritualId);
        if (instance.rewardClaimed) {
            return false;
        }
        instance.rewardClaimed = true;
        markDirty();
        return true;
    }

    public boolean isRewardClaimed(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                   Identifier ritualId) {
        AltarInstance instance = peekAltar(world, pos, ritualId);
        return instance != null && instance.rewardClaimed;
    }

    public PortalState portalState(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                   Identifier ritualId) {
        AltarInstance instance = peekAltar(world, pos, ritualId);
        return instance == null ? PortalState.CLOSED : instance.portal;
    }

    public void setPortalState(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                               Identifier ritualId, PortalState state) {
        altar(world, pos, ritualId).portal = state;
        markDirty();
    }

    // ---------- 化身维度场景（眷属实现09） ----------

    public boolean isSceneInitialized() {
        return sceneInitialized;
    }

    public int sceneTemplateVersion() {
        return sceneTemplateVersion;
    }

    public void markSceneInitialized(int templateVersion) {
        this.sceneTemplateVersion = templateVersion;
        this.sceneInitialized = true;
        markDirty();
    }
}
