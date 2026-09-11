package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import org.jetbrains.annotations.Nullable;

/**
 * 眷属实现02/03：原初基座 BE。
 * 持久字段：schemaVersion、ritualId、controllerPos（所属祭坛）、requirement（物品ID/数量）、fulfilled。
 * 状态机：EMPTY(FULFILLED=false)→FULFILLED(=true)，单向；
 * 需求物在结构生成期由确定性随机写入（眷属实现04），reload 不重抽。
 * 供物投掷扫描（眷属实现05，RitualOfferingService）只查询未完成的基座。
 */
public class PrimalPedestalBlockEntity extends EndgameGeoBlockEntity {

    public static final Identifier BLOCK_ENTITY_ID = EndgameRules.id("primal_pedestal");

    private int schemaVersion = 1;
    private @Nullable Identifier ritualId;
    /** 所属祭坛控制器坐标；结构生成期写入。 */
    private @Nullable BlockPos controllerPos;
    private @Nullable Identifier requirementItem;
    private int requirementCount = 1;
    private boolean fulfilled = false;

    public PrimalPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(RegEndgameBlockEntities.PRIMAL_PEDESTAL, pos, state);
    }

    // ---------- 数据访问 ----------

    public @Nullable Identifier getRitualId() {
        return ritualId;
    }

    public @Nullable BlockPos getControllerPos() {
        return controllerPos;
    }

    public @Nullable Identifier getRequirementItem() {
        return requirementItem;
    }

    public int getRequirementCount() {
        return requirementCount;
    }

    public boolean isFulfilled() {
        return fulfilled;
    }

    /** 结构生成/管理员调试：写入需求物与实例绑定（只在未完成时可写）。 */
    public void bind(Identifier ritualId, BlockPos controllerPos, Identifier requirementItem, int count) {
        if (fulfilled) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 已完成基座 {} 不允许重新绑定需求", pos);
            return;
        }
        this.ritualId = ritualId;
        this.controllerPos = controllerPos;
        this.requirementItem = requirementItem;
        this.requirementCount = Math.max(1, count);
        markDirty();
        dispatch();
    }

    /** 献祭完成（RitualOfferingService 消费成功后调用）；单向，不回退。 */
    public void markFulfilled() {
        if (fulfilled) {
            return;
        }
        this.fulfilled = true;
        markDirty();
        if (world != null && !world.isClient()) {
            world.setBlockState(pos, getCachedState().with(PrimalPedestalBlock.FULFILLED, true));
        }
        dispatch();
    }

    /** 客户端图标渲染依赖的初始/更新数据同步。 */
    private void dispatch() {
        if (world != null && !world.isClient()) {
            markDirty();
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putInt("schemaVersion", schemaVersion);
        if (ritualId != null) {
            tag.putString("ritualId", ritualId.toString());
        }
        if (controllerPos != null) {
            tag.putLong("controllerPos", controllerPos.asLong());
        }
        if (requirementItem != null) {
            tag.putString("requirementItem", requirementItem.toString());
        }
        tag.putInt("requirementCount", requirementCount);
        tag.putBoolean("fulfilled", fulfilled);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        schemaVersion = tag.contains("schemaVersion") ? tag.getInt("schemaVersion") : 1;
        ritualId = tag.contains("ritualId") && !tag.getString("ritualId").isEmpty()
                ? Identifier.tryParse(tag.getString("ritualId")) : null;
        controllerPos = tag.contains("controllerPos")
                ? BlockPos.fromLong(tag.getLong("controllerPos")) : null;
        requirementItem = tag.contains("requirementItem") && !tag.getString("requirementItem").isEmpty()
                ? Identifier.tryParse(tag.getString("requirementItem")) : null;
        requirementCount = Math.max(1, tag.getInt("requirementCount"));
        fulfilled = tag.getBoolean("fulfilled");
    }

    // ---------- 客户端同步（供基座图标 BER 读取） ----------

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
}
