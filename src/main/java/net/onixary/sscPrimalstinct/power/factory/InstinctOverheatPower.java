package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.effect.InstinctOverheatingEffect;
import net.onixary.sscPrimalstinct.instinct.WanderAiController;

/**
 * ssc-primalstinct:instinct_overheat —— 本能过热触发计量条（0–100，内置隐藏属性，不持久化）。
 * 每 check_interval tick 以代理原生目标谓词扫描 radius 内可攻击目标（忽略视线，在范围内即计入）：
 * 每个目标每秒贡献 growth_per_target 增量，总增速以 max_growth_per_second 封顶（默认 20，
 * 防止大量目标把增长速率推高到过热连环触发的软锁），无目标按 decay_per_second 衰减至 0。
 * 计量到 100 清零并施加 duration_seconds 秒过热 Buff（强制接管的开关仍是该 Buff，
 * 由 WanderAiController 读取）；Buff 存在期间计量冻结（不增不衰），延续"存在时不刷新"语义。
 * 计量状态仅存于 Power 实例：重进世界/等级切换重建即清零，符合短时表现向定位。
 * 字段：radius、check_interval、growth_per_target、max_growth_per_second、decay_per_second、
 * duration_seconds、proxy_entity。
 */
public class InstinctOverheatPower extends Power {

    private static final float TRIGGER_THRESHOLD = 100.0f;

    private final float radius;
    private final int checkInterval;
    private final float growthPerTarget;
    private final float maxGrowthPerSecond;
    private final float decayPerSecond;
    private final int durationSeconds;
    private final WanderAiPower sensor;

    private float meter;
    private int tickCounter;
    private int lastTargets;
    private boolean lastFrozen;

    public InstinctOverheatPower(PowerType<?> type, LivingEntity entity, float radius, int checkInterval,
                                 float growthPerTarget, float maxGrowthPerSecond, float decayPerSecond,
                                 int durationSeconds, Identifier proxyEntity) {
        super(type, entity);
        // Apoli 默认不 tick Power：不开启则计量条永不结算（首次实现即踩此坑）
        this.setTicking();
        this.radius = Math.max(0.0f, Math.min(128.0f, radius));
        this.checkInterval = Math.max(1, checkInterval);
        this.growthPerTarget = Math.max(0.0f, growthPerTarget);
        this.maxGrowthPerSecond = Math.max(0.0f, maxGrowthPerSecond);
        this.decayPerSecond = Math.max(0.0f, decayPerSecond);
        this.durationSeconds = Math.max(1, durationSeconds);
        this.sensor = new WanderAiPower(type, entity, proxyEntity, 600);
    }

    @Override
    public void tick() {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        // Buff 存在期间计量冻结，避免 Buff 内攒条导致结束瞬间再次触发
        lastFrozen = player.hasStatusEffect(InstinctOverheatingEffect.INSTANCE);
        if (lastFrozen) return;
        if (++tickCounter < checkInterval) return;
        tickCounter = 0;
        float seconds = checkInterval / 20.0f;
        lastTargets = WanderAiController.nearbyAttackTargets(player, radius, sensor, true).size();
        // 增速封顶：目标再多的场景增长也不会超过 max_growth_per_second，防止连环过热软锁
        meter += lastTargets > 0
                ? Math.min(lastTargets * growthPerTarget, maxGrowthPerSecond) * seconds
                : -decayPerSecond * seconds;
        if (meter < 0.0f) meter = 0.0f;
        if (meter >= TRIGGER_THRESHOLD) {
            meter = 0.0f;
            player.addStatusEffect(new StatusEffectInstance(InstinctOverheatingEffect.INSTANCE,
                    durationSeconds * 20, 0, false, false, true));
        }
    }

    /** 开发读数（PerceptionSync 10 tick 同步到客户端 dev HUD）。 */
    public float getMeter() {
        return meter;
    }

    public int getLastTargets() {
        return lastTargets;
    }

    public boolean isFrozen() {
        return lastFrozen;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "instinct_overheat"),
                new SerializableData()
                        .add("radius", SerializableDataTypes.FLOAT, 8.0f)
                        .add("check_interval", SerializableDataTypes.INT, 10)
                        .add("growth_per_target", SerializableDataTypes.FLOAT, 5.0f)
                        .add("max_growth_per_second", SerializableDataTypes.FLOAT, 20.0f)
                        .add("decay_per_second", SerializableDataTypes.FLOAT, 5.0f)
                        .add("duration_seconds", SerializableDataTypes.INT, 5)
                        .add("proxy_entity", SerializableDataTypes.IDENTIFIER, new Identifier("minecraft", "ocelot")),
                data -> (type, entity) -> new InstinctOverheatPower(
                        type, entity,
                        data.getFloat("radius"),
                        data.getInt("check_interval"),
                        data.getFloat("growth_per_target"),
                        data.getFloat("max_growth_per_second"),
                        data.getFloat("decay_per_second"),
                        data.getInt("duration_seconds"),
                        data.getId("proxy_entity"))
        ).allowCondition();
    }
}
