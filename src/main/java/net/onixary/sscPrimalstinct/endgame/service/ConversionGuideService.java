package net.onixary.sscPrimalstinct.endgame.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.block.PrimalConversionPlatformBlockEntity;
import net.onixary.sscPrimalstinct.endgame.state.EndgamePlayerComponent;
import net.onixary.sscPrimalstinct.endgame.state.RegEndgameComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 眷属实现13/14：转化台接近引导消息。
 * 满级玩家首次进入转化台 50 / 25 / 8 格范围、首次站上台面时，各发一条专属消息到
 * 原版聊天框（sendMessage overlay=false，非动作栏）；每条对一名玩家只触发一次
 * （标记持久化于 EndgamePlayerComponent，重登不重弹）。
 * 距离判定按同维度最近已加载转化台；瞬移落入更近档位时按 50→25→8→站的顺序补弹未触发档。
 */
public final class ConversionGuideService {

    /** 引导档位（格）。 */
    private static final double[] THRESHOLDS = {50.0, 25.0, 8.0};
    private static final String[] RANGE_KEYS = {
            "ssc-primalstinct.endgame.dialog.conversion.50",
            "ssc-primalstinct.endgame.dialog.conversion.25",
            "ssc-primalstinct.endgame.dialog.conversion.8"};
    private static final String STAND_KEY = "ssc-primalstinct.endgame.dialog.conversion.stand";
    private static final String[] FLAG_KEYS = {
            "conversion_guide_50", "conversion_guide_25", "conversion_guide_8", "conversion_guide_stand"};

    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 已加载转化台（BE 加载/卸载追踪；站台判定复用平台 BE 的检测区域口径）。 */
    private static final Map<BlockPos, PrimalConversionPlatformBlockEntity> PLATFORMS = new ConcurrentHashMap<>();

    private ConversionGuideService() {
    }

    public static void register() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof PrimalConversionPlatformBlockEntity platform) {
                PLATFORMS.put(platform.getPos(), platform);
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof PrimalConversionPlatformBlockEntity platform) {
                PLATFORMS.remove(platform.getPos());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ConversionGuideService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (PLATFORMS.isEmpty() || server.getTicks() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!EndgameEligibility.atMaxLevel(player)) {
                continue;
            }
            if (!(player.getWorld() instanceof ServerWorld world)) {
                continue;
            }
            // 同维度最近转化台
            PrimalConversionPlatformBlockEntity nearest = null;
            double bestSq = Double.MAX_VALUE;
            for (PrimalConversionPlatformBlockEntity platform : PLATFORMS.values()) {
                if (platform.getWorld() != world || platform.isRemoved()) {
                    continue;
                }
                double sq = platform.getPos().getSquaredDistance(player.getPos());
                if (sq < bestSq) {
                    bestSq = sq;
                    nearest = platform;
                }
            }
            if (nearest == null) {
                continue;
            }
            EndgamePlayerComponent component = RegEndgameComponent.ENDGAME.get(player);
            // 50→25→8 按序补弹未触发档（瞬移落入更近档时也保持顺序）
            for (int i = 0; i < THRESHOLDS.length; i++) {
                if (bestSq <= THRESHOLDS[i] * THRESHOLDS[i] && !component.isNotified(FLAG_KEYS[i])) {
                    component.markNotified(FLAG_KEYS[i]);
                    player.sendMessage(Text.translatable(RANGE_KEYS[i]).formatted(Formatting.RED), false);  // 聊天框（红色）
                }
            }
            // 首次站上台面。转化台碰撞箱仅 12px：脚部(0.75)落在台面自身格内，
            // 因此脚下格 == 转化台（矮块）或脚部格 == 转化台（满块）两种口径都算站上
            BlockPos feet = player.getBlockPos();
            boolean standingOn = feet.equals(nearest.getPos()) || feet.down().equals(nearest.getPos());
            if (standingOn && player.isOnGround() && !component.isNotified(FLAG_KEYS[3])) {
                component.markNotified(FLAG_KEYS[3]);
                player.sendMessage(Text.translatable(STAND_KEY).formatted(Formatting.RED), false);
            }
        }
    }
}
