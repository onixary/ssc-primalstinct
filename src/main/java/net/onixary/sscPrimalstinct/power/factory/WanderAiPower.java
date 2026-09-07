package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * ssc-primalstinct:instinct_wander_ai —— 玩家无输入时由指定生物的游荡 AI 接管移动。
 * 架构参考 Naturalis VanillaMobWanderDriver（服务端隐形代理实体方案，MIT）：
 * 服务端创建不加入世界的代理生物跑原生游荡 AI，玩家跟随其运动向量。
 * 不控制镜头，鼠标转向不影响接管；移动/跳跃/潜行/攻击/使用按键释放控制权。
 * 字段：entity（代理生物 ID，须为 PathAwareEntity 系）；afk_ticks（无输入等待 tick）。
 * wander_chance：游荡随机抽取参数，越小越频繁，默认 120，最小 1（不是固定等待 tick）。
 * speed_multiplier：代理移动属性倍率，默认 1；非正数或非有限值回退到 1。
 * jump_height_multiplier：代理陆地跳跃高度倍率，默认 1；按原版重力/阻力换算起跳速度。
 * 激活/门槛由 Apoli condition 表达（如本能等级条件）。
 */
public class WanderAiPower extends Power {

    private final Identifier proxyEntity;
    private final int afkTicks;
    private final int wanderChance;
    private final float speedMultiplier;
    private final float jumpHeightMultiplier;

    public WanderAiPower(PowerType<?> type, LivingEntity entity, Identifier proxyEntity, int afkTicks) {
        this(type, entity, proxyEntity, afkTicks, 120, 1.0f);
    }

    public WanderAiPower(PowerType<?> type, LivingEntity entity, Identifier proxyEntity, int afkTicks,
                         int wanderChance, float speedMultiplier) {
        this(type, entity, proxyEntity, afkTicks, wanderChance, speedMultiplier, 1.0f);
    }

    public WanderAiPower(PowerType<?> type, LivingEntity entity, Identifier proxyEntity, int afkTicks,
                         int wanderChance, float speedMultiplier, float jumpHeightMultiplier) {
        super(type, entity);
        this.proxyEntity = proxyEntity;
        this.afkTicks = afkTicks;
        this.wanderChance = Math.max(1, wanderChance);
        this.speedMultiplier = Float.isFinite(speedMultiplier) && speedMultiplier > 0
                ? speedMultiplier : 1.0f;
        this.jumpHeightMultiplier = Float.isFinite(jumpHeightMultiplier) && jumpHeightMultiplier > 0
                ? jumpHeightMultiplier : 1.0f;
    }

    public Identifier getProxyEntity() {
        return proxyEntity;
    }

    public int getAfkTicks() {
        return afkTicks;
    }

    public int getWanderChance() {
        return wanderChance;
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public float getJumpHeightMultiplier() {
        return jumpHeightMultiplier;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "instinct_wander_ai"),
                new SerializableData()
                        .add("entity", SerializableDataTypes.IDENTIFIER)
                        .add("afk_ticks", SerializableDataTypes.INT, 200)
                        .add("wander_chance", SerializableDataTypes.INT, 120)
                        .add("speed_multiplier", SerializableDataTypes.FLOAT, 1.0f)
                        .add("jump_height_multiplier", SerializableDataTypes.FLOAT, 1.0f),
                data -> (powerType, livingEntity) -> new WanderAiPower(
                        powerType, livingEntity,
                        data.getId("entity"),
                        data.getInt("afk_ticks"),
                        data.getInt("wander_chance"),
                        data.getFloat("speed_multiplier"),
                        data.getFloat("jump_height_multiplier"))
        ).allowCondition();
    }
}
