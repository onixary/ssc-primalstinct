package net.onixary.sscPrimalstinct.endgame.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.onixary.sscPrimalstinct.endgame.EndgameRules;

/**
 * 眷属实现11：终局实体注册。
 * 化身仅演出展示用：MISC 组、0.35³ 小碰撞箱（可嵌入方块）、无刷蛋/无自然生成。
 */
public final class RegEndgameEntities {

    public static final EntityType<PrimalAvatarEntity> PRIMAL_AVATAR =
            Registry.register(Registries.ENTITY_TYPE,
                    EndgameRules.id("primal_avatar"),
                    EntityType.Builder.<PrimalAvatarEntity>create(PrimalAvatarEntity::new, SpawnGroup.MISC)
                            .setDimensions(0.35f, 0.35f)
                            .build("primal_avatar"));

    private RegEndgameEntities() {
    }

    public static void registerAll() {
        // 注册在静态字段完成；此入口供主入口显式触发类加载（幂等）
    }
}
