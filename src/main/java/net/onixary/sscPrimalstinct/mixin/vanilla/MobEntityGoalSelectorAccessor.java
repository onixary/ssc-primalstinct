package net.onixary.sscPrimalstinct.mixin.vanilla;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access used only while configuring unspawned wander proxies. */
@Mixin(MobEntity.class)
public interface MobEntityGoalSelectorAccessor {
    @Accessor("goalSelector")
    GoalSelector primalstinct$getGoalSelector();
}
