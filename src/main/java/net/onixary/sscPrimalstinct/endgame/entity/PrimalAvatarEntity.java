package net.onixary.sscPrimalstinct.endgame.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;
import net.onixary.sscPrimalstinct.endgame.worldgen.AvatarSanctum;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 眷属实现11：原始化身实体（仅演出展示用）。
 * 出生锚点 = 转化台向下一格（AVATAR_ANCHOR），实体直接生成在方块内且不替换方块：
 * 碰撞箱 0.35³ 小于一格；无重力、无方块物理（不顶出）；一切伤害路径拒绝（普通实体不吃窒息），
 * 管理员仍可用 /kill（GENERIC_KILL）移除作为维护通道。
 * 不寻路、不掉落经验/战利品、不可被推挤、不可穿门；被移动则每 tick 回锚。
 * 动画：idle 循环 + interact 触发（triggerAnim 服务端广播）；同一播放窗口内的
 * 新触发请求合并（不每 tick 重启）；化身缺失不影响转化（眷属实现13 独立结算）。
 */
public class PrimalAvatarEntity extends Entity implements GeoEntity {

    /** interact 播放窗口（tick）：窗口内的新请求视为同次表现，不重启动画。 */
    public static final int INTERACT_WINDOW_TICKS = 40;
    /** 化身面朝方向：转化台在 z=52，出生点在 z≈0 → 朝北（yaw 180）。 */
    public static final float ANCHOR_YAW = 180.0f;

    private static final RawAnimation IDLE = RawAnimation.begin()
            .then("animation.primal_avatar.idle", software.bernie.geckolib.core.animation.Animation.LoopType.LOOP);
    private static final RawAnimation INTERACT = RawAnimation.begin()
            .then("animation.primal_avatar.interact", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private long interactUntilTick = Long.MIN_VALUE;

    public PrimalAvatarEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        // 无跟踪数据（纯演出实体）
    }

    // ---------- 无敌与不可交互 ----------

    @Override
    public boolean damage(DamageSource source, float amount) {
        // 管理员维护通道：仅放行 /kill 的 GENERIC_KILL，其余伤害路径全部拒绝
        return source.isOf(DamageTypes.GENERIC_KILL) && super.damage(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return !damageSource.isOf(DamageTypes.GENERIC_KILL) || super.isInvulnerableTo(damageSource);
    }

    @Override
    public boolean canUsePortals() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 嵌入方块的展示实体：不做任何“顶出方块”修正。 */
    @Override
    protected void pushOutOfBlocks(double x, double y, double z) {
    }

    @Override
    public boolean isSilent() {
        return true;
    }

    // ---------- 固定锚点 ----------

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient()) {
            return;
        }
        BlockPos anchor = AvatarSanctum.AVATAR_ANCHOR;
        Vec3d target = new Vec3d(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        if (squaredDistanceTo(target.x, target.y, target.z) > 0.04) {
            setPosition(target.x, target.y, target.z);
            setYaw(ANCHOR_YAW);
            setVelocity(Vec3d.ZERO);
            velocityDirty = true;
        }
    }

    // ---------- 动画（GeckoLib） ----------

    public void playInteract() {
        long now = getWorld().getTime();
        if (now < interactUntilTick) {
            return;  // 播放窗口内合并新请求
        }
        interactUntilTick = now + INTERACT_WINDOW_TICKS;
        triggerAnim("interact_controller", "animation.primal_avatar.interact");
    }

    /**
     * 触发转化台附近的化身 Interact（眷属实现11/13）。
     * 只查转化台有限半径内的化身，选最近一只；无化身/异维度时静默跳过（不阻断转化）。
     */
    public static void tryPlayInteract(ServerPlayerEntity player, @Nullable UUID sessionId, BlockPos platformPos) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double radius = EndgameRules.AVATAR_INTERACT_RADIUS;
        Box area = new Box(platformPos).expand(radius);
        List<PrimalAvatarEntity> nearby = world.getEntitiesByClass(PrimalAvatarEntity.class, area, Entity::isAlive);
        if (nearby.isEmpty()) {
            return;
        }
        nearby.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(
                platformPos.getX() + 0.5, platformPos.getY() + 0.5, platformPos.getZ() + 0.5)));
        nearby.get(0).playInteract();
        SSCPrimalstinct.LOGGER.debug("[primalstinct] 化身 Interact 触发（玩家 {}，平台 {}）",
                player.getGameProfile().getName(), platformPos);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 0, event -> {
            event.getController().setAnimation(IDLE);
            return PlayState.CONTINUE;
        }));
        // interact 由服务端 triggerAnim 触发播放；平时保持 STOP（idle 由另一控制器承担）
        controllers.add(new AnimationController<>(this, "interact_controller", 0, event -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // ---------- 序列化（无字段；锚点由场景常量派生） ----------

    @Override
    protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
    }
}
