package net.onixary.sscPrimalstinct.mixin.vanilla.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.sscPrimalstinct.client.network.WanderMotionClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 卡11 UI 反馈：游荡接管期间，本地玩家第三人称身体朝移动方向缓动。
 * 原版 turnHead 将身体钳制在视角±50°内、并按 95°/265° 选择倒退目标，
 * 接管期间视角静止（鼠标自由但不跟随移动），导致模型侧移/倒退观感。
 * 接管期间取消钳制与倒退选择，目标角直接取 AI 速度向量方向；
 * 保留 30%/tick 缓动与肢体摆动翻转。仅改渲染用 bodyYaw——
 * 视角、相机、攻击方向、移动均不受影响；接管结束由原版逻辑自然收回。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTurnHeadMixin {

    @Shadow
    public float bodyYaw;

    @Inject(method = "turnHead", at = @At("HEAD"), cancellable = true)
    private void primalstinct$faceMovementDuringWander(float bodyRotation, float headRotation,
                                                       CallbackInfoReturnable<Float> cir) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if ((LivingEntity) (Object) this != player || !WanderMotionClientState.isActive()
                || player.getPose() != EntityPose.STANDING) {
            return;
        }
        // 与原版 tick() 的判定一致：仅水平位移足够时更新身体目标，静止时保持当前朝向
        Vec3d velocity = player.getVelocity();
        if (velocity.x * velocity.x + velocity.z * velocity.z > 0.0025000002) {
            float moveYaw = (float) (MathHelper.atan2(velocity.z, velocity.x) * 57.295776) - 90.0f;
            this.bodyYaw += MathHelper.wrapDegrees(moveYaw - this.bodyYaw) * 0.3f;
        }
        // 肢体摆动方向随头身夹角翻转（与原版一致），否则手臂/腿摆动方向与身体相反
        float headDelta = MathHelper.wrapDegrees(player.getYaw() - this.bodyYaw);
        if (headDelta <= -90.0f || headDelta >= 90.0f) {
            headRotation = -headRotation;
        }
        cir.setReturnValue(headRotation);
    }
}
