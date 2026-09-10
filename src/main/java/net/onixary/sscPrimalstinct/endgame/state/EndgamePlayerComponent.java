package net.onixary.sscPrimalstinct.endgame.state;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 眷属实现02：终局玩家持久数据（CCA 组件，独立于 PrimalstinctComponent）。
 * 持久字段：returnAnchor（个人返回锚点，死亡复制）、转化会话（sessionId/source/target/phase/platform）、
 * 已提示标记（等级召唤等一次性提示去重）。
 * 运行中的演出计时不直接复制（死亡后由对账逻辑恢复/清理）。
 */
public class EndgamePlayerComponent implements dev.onyxstudios.cca.api.v3.component.Component {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final PlayerEntity player;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** 个人返回锚点（眷属实现10）：仅“外部→化身维度”时写入。 */
    private @Nullable ReturnAnchor returnAnchor;
    /** 转化会话；phase=STARTED/FORM_APPLIED 视为运行中。 */
    private @Nullable UUID transformSessionId;
    private @Nullable Identifier transformSource;
    private @Nullable Identifier transformTarget;
    private TransformPhase transformPhase = TransformPhase.IDLE;
    private @Nullable String transformDimension;
    private long transformPlatform = Long.MIN_VALUE;
    /** 已提示标记（等级阶段首次进入等一次性提示）。 */
    private final Set<String> notifiedFlags = new HashSet<>();

    public EndgamePlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    /** 返回锚点：sourceDimension/safePos/yaw/pitch（眷属实现10）。 */
    public record ReturnAnchor(String dimension, int x, int y, int z, float yaw, float pitch) {
    }

    // ---------- 转化会话 ----------

    public boolean hasRunningSession() {
        return transformPhase == TransformPhase.STARTED || transformPhase == TransformPhase.FORM_APPLIED;
    }

    public @Nullable UUID getTransformSessionId() {
        return transformSessionId;
    }

    public @Nullable Identifier getTransformSource() {
        return transformSource;
    }

    public @Nullable Identifier getTransformTarget() {
        return transformTarget;
    }

    public TransformPhase getTransformPhase() {
        return transformPhase;
    }

    public @Nullable String getTransformDimension() {
        return transformDimension;
    }

    public @Nullable BlockPos getTransformPlatform() {
        return transformPlatform == Long.MIN_VALUE ? null : BlockPos.fromLong(transformPlatform);
    }

    /** 建立会话：先持久化数据，再由调用方启动 SSC 变形（先落盘再启动，眷属实现13）。 */
    public void startSession(UUID sessionId, Identifier source, Identifier target,
                             String dimension, BlockPos platformPos) {
        this.transformSessionId = sessionId;
        this.transformSource = source;
        this.transformTarget = target;
        this.transformDimension = dimension;
        this.transformPlatform = platformPos == null ? Long.MIN_VALUE : platformPos.asLong();
        this.transformPhase = TransformPhase.STARTED;
    }

    public void setPhase(TransformPhase phase) {
        this.transformPhase = phase;
    }

    /** 会话终态后清理（保留 notified 标记与返回锚点）。 */
    public void clearSession() {
        this.transformSessionId = null;
        this.transformSource = null;
        this.transformTarget = null;
        this.transformDimension = null;
        this.transformPlatform = Long.MIN_VALUE;
        this.transformPhase = TransformPhase.IDLE;
    }

    // ---------- 返回锚点 ----------

    public @Nullable ReturnAnchor getReturnAnchor() {
        return returnAnchor;
    }

    public void setReturnAnchor(ReturnAnchor anchor) {
        this.returnAnchor = anchor;
    }

    public void clearReturnAnchor() {
        this.returnAnchor = null;
    }

    // ---------- 已提示标记 ----------

    public boolean isNotified(String key) {
        return notifiedFlags.contains(key);
    }

    public void markNotified(String key) {
        notifiedFlags.add(key);
    }

    // ---------- NBT ----------

    @Override
    public void readFromNbt(NbtCompound tag) {
        schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 0;
        schemaVersion = CURRENT_SCHEMA_VERSION;
        returnAnchor = null;
        if (tag.contains("returnAnchor")) {
            NbtCompound anchor = tag.getCompound("returnAnchor");
            returnAnchor = new ReturnAnchor(
                    anchor.getString("dimension"),
                    anchor.getInt("x"), anchor.getInt("y"), anchor.getInt("z"),
                    anchor.getFloat("yaw"), anchor.getFloat("pitch"));
        }
        transformSessionId = tag.contains("sessionId") && !tag.getString("sessionId").isEmpty()
                ? UUID.fromString(tag.getString("sessionId")) : null;
        transformSource = parseId(tag, "source");
        transformTarget = parseId(tag, "target");
        transformDimension = tag.contains("dim") ? tag.getString("dim") : null;
        transformPlatform = tag.contains("platform") ? tag.getLong("platform") : Long.MIN_VALUE;
        transformPhase = TransformPhase.IDLE;
        if (tag.contains("phase")) {
            try {
                transformPhase = TransformPhase.valueOf(tag.getString("phase"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        notifiedFlags.clear();
        for (net.minecraft.nbt.NbtElement element : tag.getList("notified", 8)) {
            String flag = ((NbtString) element).asString();
            if (!flag.isEmpty()) {
                notifiedFlags.add(flag);
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        tag.putInt("schemaVersion", schemaVersion);
        if (returnAnchor != null) {
            NbtCompound anchor = new NbtCompound();
            anchor.putString("dimension", returnAnchor.dimension());
            anchor.putInt("x", returnAnchor.x());
            anchor.putInt("y", returnAnchor.y());
            anchor.putInt("z", returnAnchor.z());
            anchor.putFloat("yaw", returnAnchor.yaw());
            anchor.putFloat("pitch", returnAnchor.pitch());
            tag.put("returnAnchor", anchor);
        }
        if (transformSessionId != null) {
            tag.putString("sessionId", transformSessionId.toString());
        }
        if (transformSource != null) {
            tag.putString("source", transformSource.toString());
        }
        if (transformTarget != null) {
            tag.putString("target", transformTarget.toString());
        }
        if (transformDimension != null) {
            tag.putString("dim", transformDimension);
        }
        if (transformPlatform != Long.MIN_VALUE) {
            tag.putLong("platform", transformPlatform);
        }
        tag.putString("phase", transformPhase.name());
        if (!notifiedFlags.isEmpty()) {
            NbtList list = new NbtList();
            for (String flag : notifiedFlags) {
                list.add(NbtString.of(flag));
            }
            tag.put("notified", list);
        }
    }

    private static @Nullable Identifier parseId(NbtCompound tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        String raw = tag.getString(key);
        return raw.isEmpty() ? null : Identifier.tryParse(raw);
    }
}
