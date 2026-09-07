package net.onixary.sscPrimalstinct.instinct;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.onixary.sscPrimalstinct.network.WanderMotionS2C;
import net.minecraft.registry.Registries;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.mixin.vanilla.MobEntityGoalSelectorAccessor;
import net.onixary.sscPrimalstinct.power.factory.WanderAiPower;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 游荡 AI 接管（instinct_wander_ai Power 的服务端驱动，架构移植自 Naturalis
 * VanillaMobWanderDriver，MIT）：为玩家创建一个不加入世界的隐形代理生物跑原生游荡 AI，
 * 每 tick 将代理锚定到玩家位置、更新代理 AI、把代理运动向量经
 * 水平移动指令及一次性起跳速度发给玩家客户端，重力和位置上报走原版移动链路，
 * 仅接管位移，不修改玩家视角。
 *
 * 接管条件：Power 活跃 + 托管 + 未锁定 + 非睡眠（原版/蜷缩）+ 无输入持续 afk_ticks。
 * 释放（任一即弃）：直接按键输入（updateInput mixin）、客户端按键上报（C2S）、
 * 疾跑、非站立姿势、Power 失效/锁定/睡眠、断线/接管态变化。
 * 游荡中被驱动的位移/速度/转向不计为“玩家输入”（与 Naturalis 的信号护栏一致）。
 */
public final class WanderAiController {

    /** 空闲活动检测阈值；运动仅检测水平分量。 */
    private static final double MOVE_DELTA_SQ = 1.0E-8;
    private static final double VELOCITY_THRESHOLD_SQ = 1.0E-4;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        long lastActiveTick;
        double lastX;
        double lastZ;
        boolean wandering;
        @Nullable MobEntity proxy;
        @Nullable Identifier proxyEntityId;
        @Nullable WanderAiPower proxyPower;
    }

    private WanderAiController() {
    }

    public static void tickPlayer(ServerPlayerEntity player) {
        State state = STATES.computeIfAbsent(player.getUuid(), uuid -> {
            State initial = new State();
            initial.lastActiveTick = player.age;
            refreshPositionBaseline(player, initial);
            return initial;
        });
        long now = player.age;
        WanderAiPower power = findActivePower(player);
        boolean blocked = !player.isAlive() || player.isSpectator() || player.hasVehicle()
                || player.getAbilities().flying || power == null
                || !PrimalstinctLifecycle.isManaged(player)
                || net.onixary.sscPrimalstinct.component.RegPrimalstinctComponent.PRIMALSTINCT.get(player).isLocked()
                || player.isSleeping()
                || net.onixary.sscPrimalstinct.sleep.CurlSleepController.isCurlSleeping(player);
        if (blocked) {
            if (state.wandering) {
                discard(player, state);
            }
            state.lastActiveTick = now;
            refreshPositionBaseline(player, state);
            return;
        }

        // 活动检测：游荡中排除被驱动的位移/速度/转向（护栏同 Naturalis）
        boolean activity = WanderAiController.hasDirectInputSignal(player, now)
                || player.isSprinting()
                || player.getPose() != EntityPose.STANDING;
        if (!state.wandering) {
            Vec3d pos = player.getPos();
            boolean moved = distanceSq2d(pos.x - state.lastX, pos.z - state.lastZ) > MOVE_DELTA_SQ;
            // Grounded players retain gravity velocity (~-0.0784); only horizontal motion is activity.
            boolean velocity = player.getVelocity().horizontalLengthSquared() > VELOCITY_THRESHOLD_SQ;
            activity |= moved || velocity;
        }
        refreshPositionBaseline(player, state);
        if (activity) {
            state.lastActiveTick = now;
        }

        if (state.wandering) {
            if (activity) {
                discard(player, state);  // 任意输入立即释放
                return;
            }
            drive(player, power, state);
        } else if (now - state.lastActiveTick >= Math.max(1, power.getAfkTicks())) {
            start(player, power, state);
            if (state.wandering) {
                drive(player, power, state);
            }
        }
    }

    private static void refreshPositionBaseline(ServerPlayerEntity player, State state) {
        Vec3d pos = player.getPos();
        state.lastX = pos.x;
        state.lastZ = pos.z;
    }

    private static boolean hasDirectInputSignal(ServerPlayerEntity player, long now) {
        return DirectInputTracker.hasInputSince(player.getUuid(), now - 2L);
    }

    private static void start(ServerPlayerEntity player, WanderAiPower power, State state) {
        MobEntity proxy = createProxy(player, power);
        if (proxy == null) {
            state.lastActiveTick = player.age;  // 创建失败退避：下个 idle 周期再试
            return;
        }
        state.proxy = proxy;
        state.proxyEntityId = power.getProxyEntity();
        state.proxyPower = power;
        state.wandering = true;
        SSCPrimalstinct.LOGGER.info("[primalstinct] 玩家 {} 本能游荡接管开始（模拟 {}）",
                player.getGameProfile().getName(), power.getProxyEntity());
    }

    private static void drive(ServerPlayerEntity player, WanderAiPower power, State state) {
        MobEntity proxy = state.proxy;
        if (proxy == null || !proxy.isAlive() || proxy.getWorld() != player.getWorld()
                || state.proxyPower != power
                || !power.getProxyEntity().equals(state.proxyEntityId)) {
            discardProxyEntity(player, state);
            MobEntity replacement = createProxy(player, power);
            if (replacement == null) {
                discard(player, state);
                state.lastActiveTick = player.age;
                return;
            }
            proxy = replacement;
            state.proxy = proxy;
            state.proxyEntityId = power.getProxyEntity();
            state.proxyPower = power;
        }

        // 1) 锚定代理到玩家（复制落地/坠落状态，AI 在玩家当前位置决策）
        Vec3d anchor = player.getPos();
        proxy.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, proxy.getYaw(), proxy.getPitch());
        proxy.setOnGround(player.isOnGround());
        proxy.fallDistance = player.fallDistance;

        // tickNewAi schedules new goals by (serverTicks + entityId) parity.
        // Skipping every other server tick can permanently starve goal acquisition.
        try {
            // Unspawned proxies do not receive ServerWorld.tickEntity's age increment.
            proxy.age++;
            // ServerWorld normally calls checkDespawn before ticking, which resets this
            // counter for persistent/nearby mobs. Unspawned proxies miss that callback.
            // WanderAroundGoal refuses to start once the counter reaches 100.
            proxy.setDespawnCounter(0);
            proxy.tick();
        } catch (Throwable t) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 游荡代理 {} tick 异常，释放接管",
                    power.getProxyEntity(), t);
            discard(player, state);
            state.lastActiveTick = player.age;
            return;
        }

        // Send horizontal intent only. Moving the server player here and also sending
        // full velocity makes vanilla client movement disagree, especially during falls.
        Vec3d motion = proxy.getVelocity();
        double jump = ((WanderJumpSettings) proxy).primalstinct$consumeJumpVelocity();
        new WanderMotionS2C(true, motion.x, motion.z,
                player.isOnGround() ? jump : Double.NaN).send(player);

    }

    @Nullable
    private static MobEntity createProxy(ServerPlayerEntity player, WanderAiPower power) {
        Identifier entityId = power.getProxyEntity();
        EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(entityId).orElse(null);
        if (type == null) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 游荡代理实体 {} 不存在（instinct_wander_ai 配置）", entityId);
            return null;
        }
        net.minecraft.entity.Entity created;
        try {
            created = type.create(player.getServerWorld());
        } catch (Throwable t) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 游荡代理实体 {} 创建失败", entityId, t);
            return null;
        }
        if (!(created instanceof MobEntity mob)) {
            if (created != null) {
                created.discard();
            }
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 游荡代理实体 {} 不是生物，无法接管", entityId);
            return null;
        }
        if (!(mob instanceof PathAwareEntity)) {
            mob.discard();
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 游荡代理实体 {} 无寻路 AI（非 PathAwareEntity 系），无法接管", entityId);
            return null;
        }
        mob.setSilent(true);
        mob.setNoGravity(false);
        mob.setAiDisabled(false);
        mob.setPersistent();
        mob.setCanPickUpLoot(false);
        mob.setInvulnerable(true);
        ((WanderJumpSettings) mob).primalstinct$setJumpHeightMultiplier(power.getJumpHeightMultiplier());
        // The owner occupies the proxy's position: held food would keep TemptGoal
        // active forever while its close-range branch continuously stops navigation.
        ((MobEntityGoalSelectorAccessor) mob).primalstinct$getGoalSelector()
                .clear(goal -> goal instanceof TemptGoal);
        // Reduce random waiting through the existing goals, without extra physics ticks.
        ((MobEntityGoalSelectorAccessor) mob).primalstinct$getGoalSelector().getGoals()
                .forEach(entry -> {
                    if (entry.getGoal() instanceof WanderAroundGoal wander) {
                        wander.setChance(power.getWanderChance());
                    }
                });
        var movementSpeed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(movementSpeed.getBaseValue() * power.getSpeedMultiplier());
        }
        mob.setCustomName(Text.literal("primalstinct_wander_proxy"));
        Vec3d pos = player.getPos();
        mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
        return mob;
    }

    /** 客户端按键上报（C2S）：立即释放；鼠标转向不影响接管。 */
    public static void markManualInput(ServerPlayerEntity player) {
        markDirectInput(player);
        State state = STATES.get(player.getUuid());
        if (state != null) {
            state.lastActiveTick = player.age;
            if (state.wandering) {
                discard(player, state);
            }
        }
    }

    /** updateInput mixin 调用：服务端直接捕获的按键输入（2 tick 容差包住网络抖动）。 */
    public static void markDirectInput(ServerPlayerEntity player) {
        DirectInputTracker.mark(player.getUuid(), player.age);
    }

    private static void discard(ServerPlayerEntity player, State state) {
        discardProxyEntity(player, state);
        state.wandering = false;
        WanderMotionS2C.clear(player);
    }

    private static void discardProxyEntity(ServerPlayerEntity player, State state) {
        if (state.proxy != null && state.proxy.isAlive()) {
            state.proxy.discard();
        }
        state.proxy = null;
        state.proxyEntityId = null;
        state.proxyPower = null;
    }

    public static void stopPlayer(ServerPlayerEntity player) {
        State state = STATES.get(player.getUuid());
        if (state != null && state.wandering) {
            discard(player, state);
        }
        clearPlayer(player.getUuid());
    }

    public static void clearPlayer(UUID uuid) {
        DirectInputTracker.remove(uuid);
        State state = STATES.remove(uuid);
        if (state != null && state.proxy != null && state.proxy.isAlive()) {
            state.proxy.discard();
        }
    }

    @Nullable
    private static WanderAiPower findActivePower(ServerPlayerEntity player) {
        for (WanderAiPower power : PowerHolderComponent.getPowers(player, WanderAiPower.class)) {
            if (power.isActive()) {
                return power;
            }
        }
        return null;
    }

    private static double distanceSq2d(double dx, double dz) {
        return dx * dx + dz * dz;
    }

    /** 服务端按键输入标记（updateInput mixin 写入；仅服务线程）。 */
    static final class DirectInputTracker {
        private static final Map<UUID, Long> LAST_INPUT_TICK = new HashMap<>();

        private DirectInputTracker() {
        }

        static void mark(UUID uuid, long tick) {
            LAST_INPUT_TICK.put(uuid, tick);
        }

        static boolean hasInputSince(UUID uuid, long minTickInclusive) {
            Long tick = LAST_INPUT_TICK.get(uuid);
            return tick != null && tick >= minTickInclusive;
        }

        static void remove(UUID uuid) {
            LAST_INPUT_TICK.remove(uuid);
        }
    }
}
