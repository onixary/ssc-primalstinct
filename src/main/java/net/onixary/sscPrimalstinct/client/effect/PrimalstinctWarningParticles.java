package net.onixary.sscPrimalstinct.client.effect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.onixary.sscPrimalstinct.adapter.ssc.SSCClientAdapter;
import net.onixary.sscPrimalstinct.client.network.ClientPrimalstinctState;
import net.onixary.sscPrimalstinct.network.PrimalstinctStateS2C;

/**
 * 卡15：高值预警粒子（新状态驱动，替换旧 InstinctUtils.clientTick 的粒子路径）。
 * - 高值区间 [次高阈值, 满值)：每 10 tick 一枚（2/s）；
 * - 满值锁定后仍保留低频提示：每 40 tick 一枚（0.5/s）；
 * - 以客户端 tick 计时（单处注册 + 冷却计数）：频率与 FPS 无关，同一 tick 重复回调也不会成倍生成。
 * 粒子类型沿用旧 SSC 的变身粒子（SSCClientAdapter.transformParticle）。
 */
@Environment(EnvType.CLIENT)
public final class PrimalstinctWarningParticles {

    private static final int HIGH_VALUE_INTERVAL_TICKS = 10;
    private static final int LOCKED_INTERVAL_TICKS = 40;

    private static int cooldownTicks;

    private PrimalstinctWarningParticles() {
    }

    public static void reset() { cooldownTicks = 0; }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PrimalstinctWarningParticles::tick);
    }

    private static void tick(MinecraftClient client) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        PrimalstinctStateS2C snapshot = ClientPrimalstinctState.snapshot();
        if (snapshot == null || !snapshot.managed()) {
            return;
        }
        float value = ClientPrimalstinctState.displayValueTick(player);
        if (Float.isNaN(value)) {
            return;
        }
        float max = snapshot.maxValue();
        if (snapshot.locked() && value >= max) {
            spawnAround(player);
            cooldownTicks = LOCKED_INTERVAL_TICKS;
        } else if (value >= warnThreshold(snapshot) && value < max) {
            spawnAround(player);
            cooldownTicks = HIGH_VALUE_INTERVAL_TICKS;
        } else {
            cooldownTicks = 1;  // 非预警区间逐 tick 复查，不生成
        }
    }

    /** 次高阈值（默认表 L4 入口 80）；等级表过短时退回 80% 满值。 */
    private static float warnThreshold(PrimalstinctStateS2C snapshot) {
        float[] thresholds = snapshot.thresholds();
        if (thresholds.length >= 2) {
            return thresholds[thresholds.length - 2];
        }
        return snapshot.maxValue() * 0.8f;
    }

    /** 与旧 SSC InstinctUtils.clientTick 相同的环绕偏移。 */
    private static void spawnAround(ClientPlayerEntity player) {
        player.getWorld().addParticle(
                SSCClientAdapter.transformParticle(),
                player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.5,
                player.getY() + player.getRandom().nextDouble() * 1.0,
                player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.5,
                0.0, 1.0, 0.5);
    }
}
