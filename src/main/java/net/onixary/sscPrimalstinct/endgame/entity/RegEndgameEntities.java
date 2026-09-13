package net.onixary.sscPrimalstinct.endgame.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;

/**
 * 眷属实现11：终局实体注册。
 * 化身为 LivingEntity（PathAwareEntity）标准渲染路径：MISC 组、0.35³ 小碰撞箱
 * （可嵌入方块）、防火、无刷蛋/无自然生成；默认属性用 LivingEntity 基础集。
 */
public final class RegEndgameEntities {

    public static final EntityType<PrimalAvatarEntity> PRIMAL_AVATAR =
            Registry.register(Registries.ENTITY_TYPE,
                    EndgameRules.id("primal_avatar"),
                    EntityType.Builder.<PrimalAvatarEntity>create(PrimalAvatarEntity::new, SpawnGroup.MISC)
                            .setDimensions(0.35f, 0.35f)
                            .makeFireImmune()
                            .build("primal_avatar"));

    /** 眷属实现07：原初残余飞行实体（末影之眼同款参数：MISC、0.25³、跟踪 4/间隔 4）。 */
    public static final EntityType<PrimalRemnantEntity> PRIMAL_REMNANT =
            Registry.register(Registries.ENTITY_TYPE,
                    EndgameRules.id("primal_remnant"),
                    EntityType.Builder.<PrimalRemnantEntity>create(PrimalRemnantEntity::new, SpawnGroup.MISC)
                            .setDimensions(0.25f, 0.25f)
                            .maxTrackingRange(4)
                            .trackingTickInterval(4)
                            .build("primal_remnant"));

    private RegEndgameEntities() {
    }

    public static void registerAll() {
        // LivingEntity 必需：默认属性注册（缺少会在实体创建时崩溃）
        // 注意必须用 createMobAttributes（含 follow_range）：Mob 构造器的导航初始化会读取它
        FabricDefaultAttributeRegistry.register(PRIMAL_AVATAR,
                net.minecraft.entity.mob.MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 1024.0));
    }
}
