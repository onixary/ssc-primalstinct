package net.onixary.sscPrimalstinct.endgame.worldgen;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.block.RegEndgameBlocks;
import net.onixary.sscPrimalstinct.endgame.state.EndgameWorldState;
import net.onixary.sscPrimalstinct.util.AvatarDimension;

/** Fixed authored NBT sanctuary. No runtime layout randomization or entity spawning. */
public final class AvatarSanctum {
    /**
     * 每次改动圣所几何都要 +1，否则已经初始化过的世界会直接返回、不会重铺。
     * 4：中央转化台外围改成带侵蚀的仪式圆环铺装。
     * 与 tools/generate_avatar_sanctum.py 的 TEMPLATE_VERSION、layout.json 的 version 保持一致。
     */
    public static final int TEMPLATE_VERSION = 4;
    public static final Identifier TEMPLATE_ID = new Identifier(SSCPrimalstinct.MOD_ID, "avatar_sanctum");
    // Keep in sync with tools/generate_avatar_sanctum.py and docs/avatar_sanctum/layout.json.
    public static final BlockPos ORIGIN = new BlockPos(-72, 0, -24);
    public static final Vec3i TEMPLATE_SIZE = new Vec3i(145, 152, 145);
    public static final BlockPos SPAWN = new BlockPos(0, 96, 0);
    /** South (+Z), directly along the surviving bridge toward the ring. */
    public static final float SPAWN_YAW = 0.0f;
    public static final BlockPos RETURN_PORTAL_CENTER = new BlockPos(0, 96, -5);
    public static final BlockPos CONVERSION_PLATFORM = new BlockPos(0, 108, 52);
    /** 化身实体出生锚点：转化台向下一格（实体直接生成在方块内，不替换方块——眷属实现11）。 */
    public static final BlockPos AVATAR_ANCHOR = CONVERSION_PLATFORM.down();

    private AvatarSanctum() {}

    public static boolean ensureReady(MinecraftServer server) {
        ServerWorld world = AvatarDimension.world(server);
        if (world == null) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 原始化身维度未注册");
            return false;
        }
        EndgameWorldState state = EndgameWorldState.get(world);
        if (state.isSceneInitialized() && state.sceneTemplateVersion() >= TEMPLATE_VERSION) {
            return ensureAvatarEntity(world);  // 场景就绪也幂等核验化身（缺则补，重复去重）
        }
        var optional = world.getStructureTemplateManager().getTemplate(TEMPLATE_ID);
        if (optional.isEmpty() || !optional.get().getSize().equals(TEMPLATE_SIZE)) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 圣所模板缺失或尺寸错误：{}，需要 {}", TEMPLATE_ID, TEMPLATE_SIZE);
            return false;
        }
        var template = optional.get();
        var placement = new StructurePlacementData().setIgnoreEntities(true);
        var platforms = template.getInfosForBlock(ORIGIN, placement, RegEndgameBlocks.PRIMAL_CONVERSION_PLATFORM);
        var portals = template.getInfosForBlock(ORIGIN, placement, RegEndgameBlocks.PRIMAL_PORTAL);
        if (platforms.size() != 1 || !platforms.get(0).pos().equals(CONVERSION_PLATFORM)
                || portals.size() != 9 || portals.stream().noneMatch(p -> p.pos().equals(RETURN_PORTAL_CENTER))) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 圣所模板功能锚点错误：{}", TEMPLATE_ID);
            return false;
        }
        // One-time placement only; sparse NBT holds authored solids, not millions of air blocks.
        int maxX = ORIGIN.getX() + TEMPLATE_SIZE.getX() - 1;
        int maxZ = ORIGIN.getZ() + TEMPLATE_SIZE.getZ() - 1;
        for (int cx = ORIGIN.getX() >> 4; cx <= (maxX >> 4); cx++) {
            for (int cz = ORIGIN.getZ() >> 4; cz <= (maxZ >> 4); cz++) {
                world.getChunk(cx, cz);
            }
        }
        // Upgrade only the known old placeholder volume; never clear the entire dimension.
        if (state.isSceneInitialized() && state.sceneTemplateVersion() <= 2) {
            clearLegacyPlaceholder(world);
        }
        if (!template.place(world, ORIGIN, ORIGIN, placement, world.getRandom(),
                Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS)) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 圣所模板放置失败：{}", TEMPLATE_ID);
            return false;
        }
        state.markSceneInitialized(TEMPLATE_VERSION);
        ensureAvatarEntity(world);
        for (var player : world.getPlayers()) {
            if (isInsideLegacyPlaceholder(player.getBlockPos())) {
                player.teleport(world, SPAWN.getX() + 0.5, SPAWN.getY(), SPAWN.getZ() + 0.5, SPAWN_YAW, 0);
            }
        }
        SSCPrimalstinct.LOGGER.info("[primalstinct] 原始化身裂缝场景已就绪（template={}，version={}）",
                TEMPLATE_ID, TEMPLATE_VERSION);
        return true;
    }

    private static void clearLegacyPlaceholder(ServerWorld world) {
        for (BlockPos pos : BlockPos.iterate(-8, 63, -8, 8, 64, 8)) {
            var block = world.getBlockState(pos).getBlock();
            if (block == Blocks.SMOOTH_STONE || block == Blocks.POLISHED_DEEPSLATE || block == Blocks.SEA_LANTERN
                    || block == RegEndgameBlocks.PRIMAL_PORTAL || block == RegEndgameBlocks.PRIMAL_PORTAL_FRAME
                    || block == RegEndgameBlocks.PRIMAL_CONVERSION_PLATFORM) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
            }
        }
    }

    /**
     * 化身实体幂等核验（眷属实现09/11）：固定锚点出生、意外重复保留唯一合法实例。
     * 实体直接生成在锚点方块内（不替换方块）；chunk 卸载/重启随世界持久化。
     */
    private static boolean ensureAvatarEntity(ServerWorld world) {
        Vec3d center = new Vec3d(AVATAR_ANCHOR.getX() + 0.5, AVATAR_ANCHOR.getY(), AVATAR_ANCHOR.getZ() + 0.5);
        var existing = world.getEntitiesByClass(
                net.onixary.sscPrimalstinct.endgame.entity.PrimalAvatarEntity.class,
                new net.minecraft.util.math.Box(center, center).expand(16.0), e -> e.isAlive());
        if (!existing.isEmpty()) {
            if (existing.size() > 1) {
                existing.sort(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(center)));
                for (int i = 1; i < existing.size(); i++) {
                    existing.get(i).discard();
                }
                SSCPrimalstinct.LOGGER.warn("[primalstinct] 圣所化身意外重复（{} 只），已保留最近锚点的一只", existing.size());
            }
            return true;
        }
        var avatar = net.onixary.sscPrimalstinct.endgame.entity.RegEndgameEntities.PRIMAL_AVATAR.create(world);
        if (avatar == null) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 化身实体创建失败");
            return false;
        }
        avatar.refreshPositionAndAngles(center.x, center.y, center.z,
                net.onixary.sscPrimalstinct.endgame.entity.PrimalAvatarEntity.ANCHOR_YAW, 0.0f);
        world.spawnEntity(avatar);
        SSCPrimalstinct.LOGGER.info("[primalstinct] 圣所化身已就位于锚点 {}", AVATAR_ANCHOR);
        return true;
    }

    public static boolean isInsideLegacyPlaceholder(BlockPos pos) {
        return pos.getX() >= -9 && pos.getX() <= 9 && pos.getZ() >= -9 && pos.getZ() <= 9
                && pos.getY() >= 63 && pos.getY() <= 71;
    }
}
