package net.onixary.sscPrimalstinct.endgame.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.onixary.sscPrimalstinct.items.RegPrimalstinctItems;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 眷属实现07：原初残余飞行实体（借鉴原版 EyeOfEnderEntity 的目标飞行逻辑）。
 * 由 RemnantService 在击杀触发/手动使用时生成，立即启动寻路展示：
 * 远目标取 12 格航点并抬升，近目标直飞； PORTAL 拖尾；到时无条件消散
 * （消散音效+碎裂事件），绝不落回可拾取物品。
 * 目标坐标不落盘：跨重启加载的实体会作为无目标孤体在宽限期内直接消散，
 * 避免无目的滞留（眷属实现07 步骤7）。
 * 红色发光由角色 Power remnant_glow（apoli:entity_glow，公共等级表 L1）实现：
 * 客户端检测附近残余并渲染红色描边，颜色与半径在 power JSON 数据化配置。
 */
public class PrimalRemnantEntity extends Entity implements FlyingItemEntity {

    /** 飞行时长（tick）：原版末影之眼为 80，用户决策 ×2（2026-09-13）；到时 100% 消散。 */
    public static final int FLIGHT_TICKS = 160;
    /** 无目标孤体（跨重启恢复）的消散宽限期。 */
    private static final int ORPHAN_GRACE_TICKS = 40;
    /** 远目标航点距离：原版为 12，用户决策 ×1.5（2026-09-13）；超过则取航点并整体抬升。 */
    private static final double WAYPOINT_DISTANCE = 18.0;
    /** 远目标航点抬升高度：原版为 8，随航点距离 ×1.5。 */
    private static final double WAYPOINT_RISE = 12.0;

    private double targetX;
    private double targetY;
    private double targetZ;
    private int lifespan;
    private boolean launched;
    /** 归属玩家（运行期数据；仅用于在途数量统计，不落盘不参与逻辑）。 */
    private @Nullable UUID ownerUuid;

    public PrimalRemnantEntity(EntityType<? extends PrimalRemnantEntity> type, World world) {
        super(type, world);
    }

    public PrimalRemnantEntity(World world, double x, double y, double z, @Nullable UUID ownerUuid) {
        this(net.onixary.sscPrimalstinct.endgame.entity.RegEndgameEntities.PRIMAL_REMNANT, world);
        this.ownerUuid = ownerUuid;
        this.setPosition(x, y, z);
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public ItemStack getStack() {
        return new ItemStack(RegPrimalstinctItems.PRIMAL_REMNANT);
    }

    @Override
    protected void initDataTracker() {
        // 固定渲染自身物品，无需跟踪数据
    }

    /**
     * 设定飞行目标（借鉴 EyeOfEnderEntity#initTargetPos）：
     * 水平距离超过 12 格时朝目标取 12 格航点并抬升 8 格，否则直飞目标。
     */
    public void initTargetPos(BlockPos pos) {
        double dx = pos.getX() - this.getX();
        double dz = pos.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > WAYPOINT_DISTANCE) {
            this.targetX = this.getX() + dx / horizontal * WAYPOINT_DISTANCE;
            this.targetZ = this.getZ() + dz / horizontal * WAYPOINT_DISTANCE;
            this.targetY = this.getY() + WAYPOINT_RISE;
        } else {
            this.targetX = pos.getX();
            this.targetY = pos.getY();
            this.targetZ = pos.getZ();
        }
        this.launched = true;
        this.lifespan = 0;
    }

    @Override
    public boolean shouldRender(double distance) {
        double d = this.getBoundingBox().getAverageSideLength() * 4.0;
        if (Double.isNaN(d)) {
            d = 4.0;
        }
        d *= 64.0;
        return distance < d * d;
    }

    @Override
    public void setVelocityClient(double x, double y, double z) {
        this.setVelocity(x, y, z);
        // 首次速度同步时对齐朝向（与原版末影之眼一致，避免从 0 朝向插值旋转）
        if (this.prevPitch == 0.0F && this.prevYaw == 0.0F) {
            double horizontal = Math.sqrt(x * x + z * z);
            this.setYaw((float) (MathHelper.atan2(x, z) * 180.0F / (float) Math.PI));
            this.setPitch((float) (MathHelper.atan2(y, horizontal) * 180.0F / (float) Math.PI));
            this.prevYaw = this.getYaw();
            this.prevPitch = this.getPitch();
        }
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d velocity = this.getVelocity();
        double x = this.getX() + velocity.x;
        double y = this.getY() + velocity.y;
        double z = this.getZ() + velocity.z;
        double horizontalSpeed = velocity.horizontalLength();
        this.setPitch(smoothRotation(this.prevPitch,
                (float) (MathHelper.atan2(velocity.y, horizontalSpeed) * 180.0F / (float) Math.PI)));
        this.setYaw(smoothRotation(this.prevYaw,
                (float) (MathHelper.atan2(velocity.x, velocity.z) * 180.0F / (float) Math.PI)));
        if (!this.getWorld().isClient) {
            // 服务端逐 tick 重建速度：水平速率向“到目标距离”缓收敛，垂直速率向目标方向微调（与原版一致的飞行手感）
            double dx = this.targetX - x;
            double dz = this.targetZ - z;
            float horizontalDist = (float) Math.sqrt(dx * dx + dz * dz);
            float angle = (float) MathHelper.atan2(dz, dx);
            double speed = MathHelper.lerp(0.0025, horizontalSpeed, horizontalDist);
            double vertical = velocity.y;
            if (horizontalDist < 1.0F) {
                speed *= 0.8;
                vertical *= 0.8;
            }
            int verticalTarget = this.getY() < this.targetY ? 1 : -1;
            velocity = new Vec3d(Math.cos(angle) * speed, vertical + (verticalTarget - vertical) * 0.015F,
                    Math.sin(angle) * speed);
            this.setVelocity(velocity);
        }

        if (this.isTouchingWater()) {
            for (int i = 0; i < 4; i++) {
                this.getWorld().addParticle(ParticleTypes.BUBBLE,
                        x - velocity.x * 0.25, y - velocity.y * 0.25, z - velocity.z * 0.25,
                        velocity.x, velocity.y, velocity.z);
            }
        } else {
            this.getWorld().addParticle(ParticleTypes.PORTAL,
                    x - velocity.x * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                    y - velocity.y * 0.25 - 0.5,
                    z - velocity.z * 0.25 + this.random.nextDouble() * 0.6 - 0.3,
                    velocity.x, velocity.y, velocity.z);
        }

        if (!this.getWorld().isClient) {
            this.setPosition(x, y, z);
            this.lifespan++;
            boolean expired = this.launched && this.lifespan > FLIGHT_TICKS;
            boolean orphan = !this.launched && this.lifespan > ORPHAN_GRACE_TICKS;
            if (expired || orphan) {
                // 无条件消散：播放消散音效与碎裂表现，绝不生成可拾取物品（眷属实现07）
                this.playSound(SoundEvents.ENTITY_ENDER_EYE_DEATH, 1.0F, 1.0F);
                this.getWorld().syncWorldEvent(WorldEvents.EYE_OF_ENDER_BREAKS, this.getBlockPos(), 0);
                this.discard();
            }
        } else {
            this.setPos(x, y, z);
        }
    }

    /** 朝向平滑（与 ProjectileEntity#updateRotation 同逻辑，其为 protected 不可直接调用）。 */
    private static float smoothRotation(float prevRotation, float newRotation) {
        while (newRotation - prevRotation < -180.0F) {
            prevRotation -= 360.0F;
        }
        while (newRotation - prevRotation >= 180.0F) {
            prevRotation += 360.0F;
        }
        return MathHelper.lerp(0.2F, prevRotation, newRotation);
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        // 目标不落盘：重启后作为孤体消散（见类注释）
    }
    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        // 同上
    }

    @Override
    public float getBrightnessAtEyes() {
        return 1.0F;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
