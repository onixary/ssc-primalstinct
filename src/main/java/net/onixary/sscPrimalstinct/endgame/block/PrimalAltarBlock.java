package net.onixary.sscPrimalstinct.endgame.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.endgame.service.RitualClaimService;
import org.jetbrains.annotations.Nullable;

/**
 * 眷属实现03/06：原初祭坛方块。
 * 两个状态：待激活（ACTIVE=false）与已激活（ACTIVE=true），各自独立贴图；
 * 三面导线供能全部有效才置 ACTIVE（PrimalAltarBlockEntity 重算）。
 * 已激活 + 满级玩家右键 → 一次性产出镇静碎片（RitualClaimService），
 * 领取后替换为失效的原初祭坛方块（独立 Block，SPENT 不回退）。
 */
public class PrimalAltarBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    public PrimalAltarBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH).with(ACTIVE, false));
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PrimalAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        // 慢速重算三路供能（邻块变化即时重算 + 周期兜底，含跨 chunk 加载后的重验）
        BlockEntityTicker<PrimalAltarBlockEntity> recompute = (w, pos, s, entity) -> {
            if (w.getTime() % 20 == 0) {
                entity.recomputeActive();
            }
        };
        return checkType(type, RegEndgameBlockEntities.PRIMAL_ALTAR, recompute);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        // 未处理路径一律 PASS：让原版继续手中物品的使用（如镇静碎片/转化流程道具），
        // 不能用 SUCCESS/CONSUME 吃掉右键——否则对着祭坛使用物品会被连带拦截（测试问题2）
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        // 主副手重复回调共用一次性守卫：仅主手受理（副手 PASS 不拦截）
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        return RitualClaimService.tryClaim(serverPlayer, world, pos, state)
                ? ActionResult.SUCCESS : ActionResult.PASS;
    }



    /** 本地 ticker 类型核对（Yarn 1.20.1 无 BlockWithEntity.validateTicker，参照 SSC checkType 模式）。 */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityTicker<T> checkType(
            BlockEntityType<T> given, BlockEntityType<?> expected, BlockEntityTicker<?> ticker) {
        return given == expected ? (BlockEntityTicker<T>) ticker : null;
    }

    public static Settings altarSettings() {
        return FabricBlockSettings.copyOf(Blocks.OBSIDIAN).strength(-1.0f, 3600000.0f).luminance(state -> 8);
    }
}
