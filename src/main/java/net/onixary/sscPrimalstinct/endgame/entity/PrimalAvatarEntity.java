package net.onixary.sscPrimalstinct.endgame.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Colors;
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

import static net.minecraft.particle.DustParticleEffect.RED;

/**
 * 眷属实现11：原始化身实体（仅演出展示用，LivingEntity 标准渲染路径）。
 * 使用 PathAwareEntity（GeckoLib 旋转/动画/插值的标准测试路径），但关闭全部 AI 与导航。
 * 出生锚点 = 转化台向下一格（AVATAR_ANCHOR），实体生成在方块内且不替换方块：
 * 无敌标记 + 伤害全拒（窒息同被拦截）；管理员可用 /kill（GENERIC_KILL 例外）移除。
 * 不掉落经验/战利品、不自然 despawn（persistent）、不可被推挤、不可穿门；偏离即回锚。
 * 动画：idle 循环 + interact 触发；播放窗口内的新请求合并；化身缺失不影响转化。
 */
public class PrimalAvatarEntity extends PathAwareEntity implements GeoEntity {

    /** interact 播放窗口（tick）：窗口内的新请求视为同次表现，不重启动画。 */
    public static final int INTERACT_WINDOW_TICKS = 100;
    // Placeholder interaction VFX. Tune the group in spawnInteractParticles below.
    public static final int INTERACT_PARTICLE_INTERVAL_TICKS = 5;
    public static final double INTERACT_PARTICLE_Y_OFFSET = 1.5;
    public static final float ANCHOR_YAW = 180.0f;
    /** 可见性包围盒半径（格）：覆盖触手模型的完整伸展范围，防止小碰撞箱导致视锥剔除。 */
    public static final double VISIBILITY_EXTENT = 12.0;

    private static final RawAnimation IDLE = RawAnimation.begin()
            .then("animation.primal_avatar.idle", software.bernie.geckolib.core.animation.Animation.LoopType.LOOP);
    private static final RawAnimation INTERACT = RawAnimation.begin()
            .then("animation.primal_avatar.interact", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private long interactUntilTick = Long.MIN_VALUE;
    private long nextInteractParticleTick = Long.MAX_VALUE;

    public PrimalAvatarEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        setInvulnerable(true);
        setPersistent();        // 不自然 despawn
        setAiDisabled(true);    // 无目标无导航，纯展示
        setHealth(getMaxHealth());  // 满血出生（属性层已放大上限）
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

    /** 不掉落经验（战利品无 loot table 即不掉物品）。 */
    @Override
    public boolean shouldDropXp() {
        return false;
    }

    // ---------- 场景物件化：移动/装载/击退全封死 ----------

    /** 一切位移路径（水流/活塞/爆炸冲量/钓鱼钩等）全部无效——锚点由 tick 锁定。 */
    @Override
    public void move(net.minecraft.entity.MovementType movementType, Vec3d movement) {
        // no-op：静态展示实体不参与物理位移
    }

    /** 攻击/爆炸击退无效（damage 拒绝已挡攻击路径；此处兜底直接调用路径）。 */
    @Override
    public void takeKnockback(double strength, double x, double z) {
        // no-op
    }

    /** 禁止骑乘装载（船/矿车/其它容器类实体的 startRiding 会被此拦截）。 */
    @Override
    protected boolean canStartRiding(net.minecraft.entity.Entity vehicle) {
        return false;
    }

    /** 禁止拴绳牵引（拴绳会持续施加拉力）。 */
    @Override
    public boolean canBeLeashedBy(net.minecraft.entity.player.PlayerEntity player) {
        return false;
    }

    // ---------- 剔除修正 ----------

    /**
     * 视锥剔除用包围盒（眷属实现11）：实体埋地 + 碰撞箱 0.35³，默认可见盒只是一个点，
     * 大模型会随视角被整只剔除。改为以锚点为中心的慷慨盒（模型可全范围伸出地面）。
     */
    @Override
    public Box getVisibilityBoundingBox() {
        return Box.of(
                new Vec3d(AvatarSanctum.AVATAR_ANCHOR.getX() + 0.5,
                        AvatarSanctum.AVATAR_ANCHOR.getY() + VISIBILITY_EXTENT * 0.5,
                        AvatarSanctum.AVATAR_ANCHOR.getZ() + 0.5),
                VISIBILITY_EXTENT * 2, VISIBILITY_EXTENT * 2, VISIBILITY_EXTENT * 2);
    }

    // ---------- 固定锚点 ----------

    @Override
    public void tick() {
        super.tick();
        // GeoEntityRenderer renders bodyYaw, not Entity.yaw. Keep both sides and
        // interpolation history aligned even when this stationary mob has no AI.
        setYaw(ANCHOR_YAW);
        prevYaw = ANCHOR_YAW;
        setBodyYaw(ANCHOR_YAW);
        prevBodyYaw = ANCHOR_YAW;
        setHeadYaw(ANCHOR_YAW);
        prevHeadYaw = ANCHOR_YAW;
        if (getWorld().isClient()) {
            return;
        }
        BlockPos anchor = AvatarSanctum.AVATAR_ANCHOR;
        Vec3d target = new Vec3d(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        if (squaredDistanceTo(target.x, target.y, target.z) > 0.04) {
            setPosition(target.x, target.y, target.z);
            setVelocity(Vec3d.ZERO);
            velocityDirty = true;
        }
        long now = getWorld().getTime();
        if (now < interactUntilTick && now >= nextInteractParticleTick) {
            spawnInteractParticles((ServerWorld) getWorld());
            nextInteractParticleTick = now + INTERACT_PARTICLE_INTERVAL_TICKS;
        }
    }

    /** Server-broadcast placeholder group, centered 1.5 blocks above the entity origin. */
    private void spawnInteractParticles(ServerWorld world) {
        double y = getY() + INTERACT_PARTICLE_Y_OFFSET;
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                getX(), y, getZ(), 8, 0.35, 0.2, 0.35, 0.02);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                getX(), y, getZ(), 12, 0.5, 0.3, 0.5, 0.1);
        world.spawnParticles(new DustParticleEffect(RED, 1.0F),
                getX(), y, getZ(), 1, 0.5, 0.3, 0.5, 0.1);
    }

    // ---------- 动画（GeckoLib） ----------

    public void playInteract() {
        long now = getWorld().getTime();
        if (now < interactUntilTick) {
            return;  // 播放窗口内合并新请求
        }
        interactUntilTick = now + INTERACT_WINDOW_TICKS;
        nextInteractParticleTick = now;
        triggerAnim("avatar_controller", "interact");
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
        List<PrimalAvatarEntity> nearby = world.getEntitiesByClass(PrimalAvatarEntity.class, area,
                avatar -> avatar != null && avatar.isAlive());
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
        // A triggered interaction temporarily overrides idle on the same bones.
        controllers.add(new AnimationController<>(this, "avatar_controller", 0, event -> {
            event.getController().setAnimation(IDLE);
            return PlayState.CONTINUE;
        }).triggerableAnim("interact", INTERACT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
