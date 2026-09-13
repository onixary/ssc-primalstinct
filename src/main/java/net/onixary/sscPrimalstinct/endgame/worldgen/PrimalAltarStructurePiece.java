package net.onixary.sscPrimalstinct.endgame.worldgen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlock;
import net.onixary.sscPrimalstinct.endgame.block.PrimalAltarBlockEntity;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPedestalBlock;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPedestalBlockEntity;
import net.onixary.sscPrimalstinct.endgame.block.PrimalPortalFrameBlock;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 祔属实现04：原初祭坛结构 piece（恒定结构，无随机旋转）。
 *
 * <p><b>模板模式</b>：优先使用 data/ssc-primalstinct/structures/primal_altar.nbt
 * （开发世界用结构方块导出，名字 ssc-primalstinct:primal_altar）。约定：
 * 模板内恰好 1 个原初祭坛方块、恰好 3 个原初基座、导线从各基座以四向相邻连向祭坛。
 * 放置锚点 = 模板内祭坛方块落在地形表面锚位（模板整体平移自动对齐，无需把祭坛摆在角落）。
 * 绑定（ritualId/基座需求/三路导线）在 postPlace 从模板数据推导，晚于地物落成后重放，免疫覆盖。</p>
 *
 * <p><b>回退模式</b>：模板缺失时使用内置编码布局（原点=祭坛，北侧 4×4 门框）。</p>
 *
 * <p>供物固定随机：世界 seed + 结构原点 + 固定盐，从终局配置清单无放回加权抽 3 条；
 * 抽取结果随 piece 持久化（跨 chunk 分段放置可重入）。</p>
 */
public class PrimalAltarStructurePiece extends StructurePiece {

    /** 确定性随机盐（固定值；换盐=全体重抽，只在明确决策时修改）。 */
    public static final long ROLL_SALT = 0x5A17C0DEL;

    // ---------- 内置回退布局（相对祭坛原点，+z 朝南；门框在北侧） ----------
    private static final int PLATFORM_MIN_X = -5, PLATFORM_MAX_X = 5;
    private static final int PLATFORM_MIN_Z = -6, PLATFORM_MAX_Z = 4;
    private static final int CLEAR_AIR_TOP = 3;
    private static final int[][] LAYOUT_PEDESTALS = {{-4, 0}, {4, 0}, {0, 4}};
    private static final Direction[] LAYOUT_PEDESTAL_FACING = {
            Direction.WEST, Direction.EAST, Direction.SOUTH};
    private static final int[][][] LAYOUT_WIRE_PATHS = {
            {{-1, 0}, {-2, 0}, {-3, 0}},
            {{1, 0}, {2, 0}, {3, 0}},
            {{0, 1}, {0, 2}, {0, 3}}};

    private final BlockPos anchor;              // 祭坛绝对坐标（模板模式的放置锚点）
    private final Identifier ritualId;
    private final Identifier[] offerings;       // 三个基座的需求物（与基座同序）
    private final int[] offeringCounts;
    private final boolean rolled;

    public PrimalAltarStructurePiece(StructurePieceType type, BlockPos anchor,
                                     Identifier ritualId, Identifier[] offerings, int[] offeringCounts,
                                     @Nullable StructureTemplate template) {
        super(type, 0, computeBox(template, anchor));
        this.anchor = anchor;
        this.ritualId = ritualId;
        this.offerings = offerings == null ? new Identifier[3] : offerings;
        this.offeringCounts = offeringCounts == null ? new int[]{1, 1, 1} : offeringCounts;
        this.rolled = this.offerings.length >= 3
                && this.offerings[0] != null && this.offerings[1] != null && this.offerings[2] != null;
    }

    public PrimalAltarStructurePiece(NbtCompound nbt) {
        super(RegEndgameWorldgen.PRIMAL_ALTAR_PIECE, nbt);
        this.anchor = BlockPos.fromLong(nbt.getLong("anchor"));
        this.ritualId = nbt.contains("ritualId") && !nbt.getString("ritualId").isEmpty()
                ? Identifier.tryParse(nbt.getString("ritualId")) : null;
        this.offerings = new Identifier[3];
        this.offeringCounts = new int[3];
        for (int i = 0; i < 3; i++) {
            String key = "offering" + i;
            this.offerings[i] = nbt.contains(key) && !nbt.getString(key).isEmpty()
                    ? Identifier.tryParse(nbt.getString(key)) : null;
            this.offeringCounts[i] = Math.max(1, nbt.getInt("count" + i));
        }
        this.rolled = nbt.getBoolean("rolled");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putLong("anchor", anchor.asLong());
        if (ritualId != null) {
            nbt.putString("ritualId", ritualId.toString());
        }
        for (int i = 0; i < 3; i++) {
            if (offerings[i] != null) {
                nbt.putString("offering" + i, offerings[i].toString());
                nbt.putInt("count" + i, offeringCounts[i]);
            }
        }
        nbt.putBoolean("rolled", rolled);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structureAccessor,
                         ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox,
                         ChunkPos chunkPos, BlockPos pivot) {
        StructureTemplate template = loadTemplate(world);
        if (template != null) {
            // 模板模式：锚点所在 chunk 一次性放置整块模板（覆盖范围在 feature 区域内，安全跨 chunk 写入）
            if (chunkBox.contains(anchor)) {
                BlockPos templateOrigin = templateOrigin(template, anchor);
                template.place(world, templateOrigin, templateOrigin,
                        new StructurePlacementData(), random, Block.NOTIFY_LISTENERS);
                SSCPrimalstinct.LOGGER.debug("[primalstinct] 模板模式放置：anchor={}, origin={}",
                        anchor, templateOrigin);
            }
            return;
        }
        SSCPrimalstinct.LOGGER.debug("[primalstinct] 祭坛 generate（回退布局）：anchor={}, chunkBox={}", anchor, chunkBox);
        generateFallback(world, chunkBox);
    }

    /**
     * 关键方块与 BE 绑定（postPlace，地物落成后重放修复）。
     * 模板模式：重放模板 + 从模板数据推导绑定；回退模式：重放祭坛/基座/导线并绑定。
     */
    public void placeCore(StructureWorldAccess world, BlockBox chunkBox, Random random) {
        SSCPrimalstinct.LOGGER.debug("[primalstinct] 祭坛 postPlace：anchor={}, chunkBox={}", anchor, chunkBox);
        StructureTemplate template = loadTemplate(world);
        if (template != null) {
            if (chunkBox.contains(anchor)) {
                BlockPos templateOrigin = templateOrigin(template, anchor);
                template.place(world, templateOrigin, templateOrigin,
                        new StructurePlacementData(), random, Block.NOTIFY_LISTENERS);
            }
            if (ritualId == null) {
                return;
            }
            bindFromTemplate(world, template, chunkBox);
            return;
        }
        SSCPrimalstinct.LOGGER.debug("[primalstinct] 祭坛 placeCore（回退布局）：anchor={}, chunkBox={}", anchor, chunkBox);
        placeCoreFallback(world, chunkBox);
    }

    // ---------- 模板模式 ----------

    private @Nullable StructureTemplate loadTemplate(StructureWorldAccess world) {
        var manager = world.toServerWorld().getServer().getStructureTemplateManager();
        return manager.getTemplate(RegEndgameWorldgen.TEMPLATE_ID)
                .filter(t -> !t.getSize().equals(Vec3i.ZERO))
                .orElse(null);
    }

    /** 模板放置原点：模板内祭坛方块对齐到锚点。 */
    private static BlockPos templateOrigin(StructureTemplate template, BlockPos anchor) {
        BlockPos altarLocal = findSingleAltarLocal(template);
        return altarLocal == null ? anchor : anchor.subtract(altarLocal);
    }

    /** 构造期供 Structure 计算包围盒：模板存在时按模板尺寸，否则回退布局。 */
    public static BlockBox computeBox(@Nullable StructureTemplate template, BlockPos anchor) {
        if (template == null || template.getSize().equals(Vec3i.ZERO)) {
            return computeFallbackBox(anchor);
        }
        BlockPos origin = templateOrigin(template, anchor);
        Vec3i size = template.getSize();
        // 包围盒底部必须与模板底行对齐：beard_thin 会把地形整平到盒底，多减 1 会让模板永远悬空 1 格
        return new BlockBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1, origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1);
    }

    /** 模板内恰好 1 个祭坛方块 → 局部坐标；否则 null（并告警一次）。 */
    private static @Nullable BlockPos findSingleAltarLocal(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> altars = templateLocal(template, RegEndgameBlocks.PRIMAL_ALTAR);
        if (altars.size() != 1) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 模板 {} 内祭坛方块数量={}（要求恰好 1 个），回退锚点",
                    RegEndgameWorldgen.TEMPLATE_ID, altars.size());
            return null;
        }
        return altars.get(0).pos();
    }

    /** 从模板数据推导绑定：基座需求（确定性抽取）、三路导线（各基座四向相邻洪泛）。 */
    private void bindFromTemplate(StructureWorldAccess world, StructureTemplate template, BlockBox chunkBox) {
        BlockPos templateOrigin = templateOrigin(template, anchor);
        List<StructureTemplate.StructureBlockInfo> altarBlocks = templateLocal(template, RegEndgameBlocks.PRIMAL_ALTAR);
        List<StructureTemplate.StructureBlockInfo> pedestalBlocks = templateLocal(template, RegEndgameBlocks.PRIMAL_PEDESTAL);
        List<StructureTemplate.StructureBlockInfo> wireBlocks = templateLocal(template, RegEndgameBlocks.PRIMAL_ENERGY_WIRE);
        if (altarBlocks.size() != 1) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 祭坛 {}（锚点）模板数据无唯一祭坛，跳过绑定", anchor);
            return;
        }
        if (pedestalBlocks.size() != 3) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 祭坛 {} 模板数据基座数量={}（要求 3），跳过绑定",
                    anchor, pedestalBlocks.size());
            return;
        }
        BlockPos altarPos = templateOrigin.add(altarBlocks.get(0).pos());
        Set<BlockPos> wireLocals = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo wire : wireBlocks) {
            wireLocals.add(wire.pos());
        }
        List<BlockPos> pedestals = new ArrayList<>(3);
        List<List<BlockPos>> paths = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            BlockPos pedestalLocal = pedestalBlocks.get(i).pos();
            BlockPos pedestalPos = templateOrigin.add(pedestalLocal);
            paths.add(wireGroupOf(pedestalLocal, wireLocals, templateOrigin));
            pedestals.add(pedestalPos);
            if (rolled && offerings[i] != null && chunkBox.contains(pedestalPos)) {
                bindPedestal(world, pedestalPos, offerings[i], offeringCounts[i], altarPos);
            }
        }
        if (chunkBox.contains(altarPos)) {
            bindAltar(world, altarPos, pedestals, paths);
        }
    }

    /** 模板内指定方块的局部坐标清单（无旋转；getAllOf 非公开 API，用 getInfosForBlock(ORIGIN) 实现）。 */
    private static List<StructureTemplate.StructureBlockInfo> templateLocal(StructureTemplate template, Block block) {
        return template.getInfosForBlock(net.minecraft.util.math.BlockPos.ORIGIN,
                new StructurePlacementData(), block);
    }

    /** 从基座出发沿四向相邻导线洪泛，收集该路导线的绝对坐标集合（与其它路不重叠时为该路全部导线）。 */
    private static List<BlockPos> wireGroupOf(BlockPos pedestalLocal, Set<BlockPos> wireLocals, BlockPos templateOrigin) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pedestalLocal.offset(direction);
            if (wireLocals.contains(neighbor)) {
                queue.add(neighbor);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            result.add(templateOrigin.add(current));
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = current.offset(direction);
                if (wireLocals.contains(neighbor) && !visited.contains(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    // ---------- 回退模式（内置布局） ----------

    private void generateFallback(StructureWorldAccess world, BlockBox chunkBox) {
        for (int x = PLATFORM_MIN_X; x <= PLATFORM_MAX_X; x++) {
            for (int z = PLATFORM_MIN_Z; z <= PLATFORM_MAX_Z; z++) {
                boolean border = x == PLATFORM_MIN_X || x == PLATFORM_MAX_X || z == PLATFORM_MIN_Z || z == PLATFORM_MAX_Z;
                placeFallback(world, chunkBox, x, -1, z, border
                        ? Blocks.POLISHED_DEEPSLATE.getDefaultState()
                        : Blocks.SMOOTH_STONE.getDefaultState());
                for (int y = 1; y <= CLEAR_AIR_TOP; y++) {
                    placeFallback(world, chunkBox, x, y, z, Blocks.AIR.getDefaultState());
                }
            }
        }
        // 门框：北侧末地传送门式 5×5 环（去四角共 12 块，缺角不影响判定），内孔 3×3 留空
        for (int x = -2; x <= 2; x++) {
            for (int z = -6; z <= -2; z++) {
                boolean border = x == -2 || x == 2 || z == -6 || z == -2;
                boolean corner = (x == -2 || x == 2) && (z == -6 || z == -2);
                placeFallback(world, chunkBox, x, 0, z, border && !corner
                        ? RegEndgameBlocks.PRIMAL_PORTAL_FRAME.getDefaultState()
                        : Blocks.AIR.getDefaultState());
            }
        }
    }

    private void placeCoreFallback(StructureWorldAccess world, BlockBox chunkBox) {
        if (ritualId == null) {
            return;
        }
        List<BlockPos> pedestals = new ArrayList<>(3);
        List<List<BlockPos>> paths = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            BlockPos pedestalPos = placeFallback(world, chunkBox, LAYOUT_PEDESTALS[i][0], 0, LAYOUT_PEDESTALS[i][1],
                    RegEndgameBlocks.PRIMAL_PEDESTAL.getDefaultState()
                            .with(PrimalPedestalBlock.FACING, LAYOUT_PEDESTAL_FACING[i]));
            List<BlockPos> path = new ArrayList<>();
            for (int[] offset : LAYOUT_WIRE_PATHS[i]) {
                BlockPos wirePos = placeFallback(world, chunkBox, offset[0], 0, offset[1],
                        RegEndgameBlocks.PRIMAL_ENERGY_WIRE.getDefaultState());
                if (wirePos != null) {
                    path.add(wirePos);
                }
            }
            if (pedestalPos != null) {
                pedestals.add(pedestalPos);
                paths.add(path);
                if (rolled) {
                    bindPedestal(world, pedestalPos, offerings[i], offeringCounts[i], anchor);
                }
            }
        }
        BlockPos altarPos = placeFallback(world, chunkBox, 0, 0, 0,
                RegEndgameBlocks.PRIMAL_ALTAR.getDefaultState()
                        .with(PrimalAltarBlock.FACING, Direction.NORTH));
        if (altarPos != null) {
            bindAltar(world, altarPos, pedestals, paths);
        }
    }

    private BlockPos placeFallback(StructureWorldAccess world, BlockBox chunkBox, int dx, int dy, int dz, BlockState state) {
        BlockPos pos = anchor.add(dx, dy, dz);
        if (!chunkBox.contains(pos)) {
            return null;
        }
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        return pos;
    }

    private static BlockBox computeFallbackBox(BlockPos anchor) {
        return new BlockBox(
                anchor.getX() + PLATFORM_MIN_X, anchor.getY() - 1, anchor.getZ() + PLATFORM_MIN_Z,
                anchor.getX() + PLATFORM_MAX_X, anchor.getY() + CLEAR_AIR_TOP, anchor.getZ() + PLATFORM_MAX_Z);
    }

    // ---------- BE 写入（生成放置阶段落盘，眷属实现04） ----------

    private void bindPedestal(StructureWorldAccess world, BlockPos pedestalPos, Identifier offering,
                              int count, BlockPos altarPos) {
        if (world.getBlockEntity(pedestalPos) instanceof PrimalPedestalBlockEntity pedestal) {
            pedestal.bind(ritualId, altarPos, offering, count);
        } else {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 基座 {} 的 BE 未随生成创建，跳过绑定", pedestalPos);
        }
    }

    private void bindAltar(StructureWorldAccess world, BlockPos altarPos,
                           List<BlockPos> pedestals, List<List<BlockPos>> wirePaths) {
        if (world.getBlockEntity(altarPos) instanceof PrimalAltarBlockEntity altar) {
            altar.bind(ritualId, pedestals, wirePaths);
        } else {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 祭坛 {} 的 BE 未随生成创建，跳过绑定", altarPos);
        }
    }
}
