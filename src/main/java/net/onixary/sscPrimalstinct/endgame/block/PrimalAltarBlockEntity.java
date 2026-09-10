package net.onixary.sscPrimalstinct.endgame.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 眷属实现02/03/06：原初祭坛控制器 BE。
 * 记录三座基座绑定、三条导线路径与三路输入状态；奖励领取事实另存
 * EndgameWorldState（BE 被 SPENT 替换后事实仍在，门户控制不依赖本 BE——眷属实现00 决策）。
 * 状态机：WAITING(ACTIVE=false)↔READY(ACTIVE=true)→SPENT（替换为失效方块）。
 *
 * <p>A 阶段（眷属实现03）供能判定为“三基座 fulfilled”最简实现；
 * 眷属实现06 落地时升级为“三条固定路径全是自有导线且无缺块”的路径校验。</p>
 */
public class PrimalAltarBlockEntity extends BlockEntity {

    public static final Identifier BLOCK_ENTITY_ID = EndgameRules.id("primal_altar");

    private @Nullable Identifier ritualId;
    /** 三座基座的相对坐标（结构生成期写入，随结构旋转统一变换）。 */
    private final List<BlockPos> pedestals = new ArrayList<>();
    /** 三条导线路径（每条从基座到祭坛指定侧面）；A 阶段仅作数据保留。 */
    private final List<List<BlockPos>> wirePaths = new ArrayList<>();

    public PrimalAltarBlockEntity(BlockPos pos, BlockState state) {
        super(RegEndgameBlockEntities.PRIMAL_ALTAR, pos, state);
    }

    public @Nullable Identifier getRitualId() {
        return ritualId;
    }

    public List<BlockPos> getPedestals() {
        return pedestals;
    }

    public List<List<BlockPos>> getWirePaths() {
        return wirePaths;
    }

    /** 结构生成/管理员调试：写入实例绑定。 */
    public void bind(Identifier ritualId, List<BlockPos> pedestals, List<List<BlockPos>> wirePaths) {
        this.ritualId = ritualId;
        this.pedestals.clear();
        this.pedestals.addAll(pedestals);
        this.wirePaths.clear();
        this.wirePaths.addAll(wirePaths);
        markDirty();
        recomputeActive();
    }

    /**
     * 三路供能重算（眷属实现06）：每路 = 对应基座 fulfilled（ritualId 匹配）+ 路径全是自有导线且无缺块。
     * 三路都有效才置 ACTIVE；任一路无效即停止允许领取。
     * 对应 chunk 未加载视为 UNKNOWN 待重验——本轮不改变 ACTIVE（不把“没加载”当永久破坏）；
     * 已加载段的导线点亮状态照常刷新。
     */
    public void recomputeActive() {
        if (world == null || world.isClient() || ritualId == null || pedestals.size() < EndgameRules.PEDESTAL_COUNT) {
            return;
        }
        boolean[] energized = new boolean[EndgameRules.PEDESTAL_COUNT];
        boolean unknown = false;
        for (int i = 0; i < EndgameRules.PEDESTAL_COUNT; i++) {
            BlockPos pedestalPos = pedestals.get(i);
            boolean fulfilled = false;
            if (isLoaded(pedestalPos)
                    && world.getBlockEntity(pedestalPos) instanceof PrimalPedestalBlockEntity pedestal) {
                fulfilled = pedestal.isFulfilled() && ritualId.equals(pedestal.getRitualId());
            } else {
                unknown = true;
            }
            boolean pathValid = true;
            List<BlockPos> path = wirePaths.size() > i ? wirePaths.get(i) : List.of();
            if (path.isEmpty()) {
                pathValid = false;  // 未记录路径（异常数据）按无效
            }
            for (BlockPos point : path) {
                if (!isLoaded(point) || !world.getBlockState(point).isOf(RegEndgameBlocks.PRIMAL_ENERGY_WIRE)) {
                    pathValid = false;
                    break;
                }
            }
            energized[i] = fulfilled && pathValid;
            setWireLit(path, energized[i]);
        }
        if (unknown) {
            return;
        }
        boolean all = energized[0] && energized[1] && energized[2];
        BlockState state = getCachedState();
        if (state.getBlock() == RegEndgameBlocks.PRIMAL_ALTAR && state.get(PrimalAltarBlock.ACTIVE) != all) {
            if (world.setBlockState(pos, state.with(PrimalAltarBlock.ACTIVE, all)) && all
                    && world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                playActivationEffect(serverWorld);
            }
        }
    }

    /** 仅在 WAITING → READY 时广播一次；重算和重登不重复播放。 */
    private void playActivationEffect(net.minecraft.server.world.ServerWorld serverWorld) {
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 1.1;
        double centerZ = pos.getZ() + 0.5;
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2.0 * i / 40;
            serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    centerX + Math.cos(angle) * 1.4, centerY, centerZ + Math.sin(angle) * 1.4,
                    1, 0.03, 0.08, 0.03, 0.025);
        }
        serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                centerX, centerY + 0.35, centerZ, 60, 0.65, 0.5, 0.65, 0.2);
        serverWorld.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE,
                net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 1.2f);
    }

    /** 失效后导线熄灭（眷属实现06）：领取事务替换方块后由 RitualClaimService 调用。 */
    public void extinguishWires() {
        if (world == null || world.isClient()) {
            return;
        }
        for (List<BlockPos> path : wirePaths) {
            setWireLit(path, false);
        }
    }

    private void setWireLit(List<BlockPos> path, boolean lit) {
        for (BlockPos point : path) {
            if (!isLoaded(point)) {
                continue;
            }
            BlockState wireState = world.getBlockState(point);
            if (wireState.isOf(RegEndgameBlocks.PRIMAL_ENERGY_WIRE)
                    && wireState.get(PrimalEnergyWireBlock.LIT) != lit) {
                world.setBlockState(point, wireState.with(PrimalEnergyWireBlock.LIT, lit));
            }
        }
    }

    private boolean isLoaded(BlockPos point) {
        return world.isChunkLoaded(point.getX() >> 4, point.getZ() >> 4);
    }

    @Override
    protected void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (ritualId != null) {
            tag.putString("ritualId", ritualId.toString());
        }
        NbtList pedestalList = new NbtList();
        for (BlockPos pedestal : pedestals) {
            pedestalList.add(net.minecraft.nbt.NbtLong.of(pedestal.asLong()));
        }
        tag.put("pedestals", pedestalList);
        NbtList pathsList = new NbtList();
        for (List<BlockPos> path : wirePaths) {
            NbtCompound pathTag = new NbtCompound();
            NbtList points = new NbtList();
            for (BlockPos point : path) {
                points.add(net.minecraft.nbt.NbtLong.of(point.asLong()));
            }
            pathTag.put("points", points);
            pathsList.add(pathTag);
        }
        tag.put("wirePaths", pathsList);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        ritualId = tag.contains("ritualId") && !tag.getString("ritualId").isEmpty()
                ? Identifier.tryParse(tag.getString("ritualId")) : null;
        pedestals.clear();
        for (net.minecraft.nbt.NbtElement element : tag.getList("pedestals", 4)) {
            pedestals.add(BlockPos.fromLong(((net.minecraft.nbt.NbtLong) element).longValue()));
        }
        wirePaths.clear();
        for (net.minecraft.nbt.NbtElement element : tag.getList("wirePaths", 10)) {
            NbtCompound pathTag = (NbtCompound) element;
            List<BlockPos> path = new ArrayList<>();
            for (net.minecraft.nbt.NbtElement point : pathTag.getList("points", 4)) {
                path.add(BlockPos.fromLong(((net.minecraft.nbt.NbtLong) point).longValue()));
            }
            wirePaths.add(path);
        }
    }
}
