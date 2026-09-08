package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ActiveTargetGoal.class)
public interface ActiveTargetGoalAccessor {
    @Accessor("targetClass") Class<? extends LivingEntity> primalstinct$getTargetClass();
    @Accessor("targetPredicate") TargetPredicate primalstinct$getTargetPredicate();
}
