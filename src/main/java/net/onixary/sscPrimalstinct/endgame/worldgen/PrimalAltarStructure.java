package net.onixary.sscPrimalstinct.endgame.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRitualConfig;
import net.onixary.sscPrimalstinct.endgame.data.EndgameRosterManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 眷属实现04：原初祭坛世界生成结构（恒定结构，无随机旋转）。
 * 布局来源：data/ssc-primalstinct/structures/primal_altar.nbt（用户用结构方块导出的恒定模板）；
 * 模板缺失时使用内置回退布局。位置分布仍由 structure_set 随机（spacing/separation 配表）。
 * 供物固定随机保持：世界 seed + 锚点 + 固定盐确定性抽取（同 seed 同供物）。
 */
public class PrimalAltarStructure extends Structure {

    public static final Codec<PrimalAltarStructure> CODEC = createCodec(PrimalAltarStructure::new);

    public PrimalAltarStructure(Config config) {
        super(config);
    }

    @Override
    public Optional<StructurePosition> getStructurePosition(Context context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getCenterX();
        int z = chunkPos.getCenterZ();
        int y = context.chunkGenerator().getHeightOnGround(x, z, Heightmap.Type.WORLD_SURFACE_WG,
                context.world(), context.noiseConfig());
        // 高度图包含水面，仅比较海平面不能排除海洋、河流或高处湖泊。
        if (y < context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        // getHeightOnGround 返回首个空气格（地表+1）；实测整体高了一格，锚点下移 1 贴地
        BlockPos anchor = new BlockPos(x, y - 1, z);
        StructureTemplate template = context.structureTemplateManager()
                .getTemplate(RegEndgameWorldgen.TEMPLATE_ID)
                .filter(t -> !t.getSize().equals(net.minecraft.util.math.Vec3i.ZERO))
                .orElse(null);
        var footprint = PrimalAltarStructurePiece.computeBox(template, anchor);
        // 使用生成器采样，不加载邻接区块；检查完整占地，不能只检查祭坛中心。
        for (int checkX = footprint.getMinX(); checkX <= footprint.getMaxX(); checkX++) {
            for (int checkZ = footprint.getMinZ(); checkZ <= footprint.getMaxZ(); checkZ++) {
                int surface = context.chunkGenerator().getHeightOnGround(checkX, checkZ,
                        Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
                var column = context.chunkGenerator().getColumnSample(checkX, checkZ,
                        context.world(), context.noiseConfig());
                if (!column.getState(surface - 1).getFluidState().isEmpty()) {
                    return Optional.empty();
                }
                // 地表覆冰/有悬挑时，模板实际放置体积也不能与流体相交。
                for (int checkY = footprint.getMinY(); checkY <= Math.min(surface - 1, footprint.getMaxY()); checkY++) {
                    if (!column.getState(checkY).getFluidState().isEmpty()) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.of(new StructurePosition(anchor, collector -> addPieces(collector, context, anchor, template)));
    }

    private static void addPieces(StructurePiecesCollector collector, Context context, BlockPos anchor,
                                  @Nullable StructureTemplate template) {
        RollResult roll = rollOfferings(context.seed(), anchor);
        collector.addPiece(new PrimalAltarStructurePiece(
                RegEndgameWorldgen.PRIMAL_ALTAR_PIECE, anchor,
                roll.ritualId, roll.items, roll.counts, template));
    }

    /** 确定性抽取：世界 seed + 锚点 + 固定盐 → 从配置清单无放回加权抽 3 条。 */
    private static RollResult rollOfferings(long worldSeed, BlockPos origin) {
        EndgameRitualConfig config = EndgameRosterManager.active();
        List<EndgameRitualConfig.OfferingEntry> pool = new ArrayList<>(config.offerings);
        if (pool.size() < 3) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 供物候选不足 3 条（{}），祭坛 {} 以未绑定状态生成",
                    pool.size(), origin);
            return new RollResult(config.ritualId, null, null);
        }
        Random random = Random.create(worldSeed ^ (origin.getX() * 341873128712L + origin.getZ() * 132897987541L)
                ^ PrimalAltarStructurePiece.ROLL_SALT);
        Identifier[] items = new Identifier[3];
        int[] counts = new int[3];
        for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
            int totalWeight = 0;
            for (EndgameRitualConfig.OfferingEntry entry : pool) {
                totalWeight += entry.weight();
            }
            int pick = random.nextInt(totalWeight);
            EndgameRitualConfig.OfferingEntry chosen = pool.get(pool.size() - 1);
            for (EndgameRitualConfig.OfferingEntry entry : pool) {
                pick -= entry.weight();
                if (pick < 0) {
                    chosen = entry;
                    break;
                }
            }
            items[i] = chosen.item();
            counts[i] = chosen.count();
            pool.remove(chosen);  // 无放回，保证三基座互不相同
        }
        return new RollResult(config.ritualId, items, counts);
    }

    private record RollResult(Identifier ritualId, Identifier[] items, int[] counts) {
    }

    /**
     * 地物落成后重放模板并绑定 BE（眷属实现04：关键方块免疫地物覆盖）。
     */
    @Override
    public void postPlace(net.minecraft.world.StructureWorldAccess world,
                          net.minecraft.world.gen.StructureAccessor structureAccessor,
                          net.minecraft.world.gen.chunk.ChunkGenerator chunkGenerator,
                          net.minecraft.util.math.random.Random random,
                          net.minecraft.util.math.BlockBox box,
                          net.minecraft.util.math.ChunkPos chunkPos,
                          net.minecraft.structure.StructurePiecesList pieces) {
        super.postPlace(world, structureAccessor, chunkGenerator, random, box, chunkPos, pieces);
        for (net.minecraft.structure.StructurePiece piece : pieces.pieces()) {
            if (piece instanceof PrimalAltarStructurePiece altarPiece) {
                altarPiece.placeCore(world, box, random);
            }
        }
    }

    @Override
    public StructureType<?> getType() {
        return RegEndgameWorldgen.PRIMAL_ALTAR_TYPE;
    }
}
