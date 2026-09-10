package net.onixary.sscPrimalstinct.endgame.state;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 眷属实现02：终局组件 CCA 注册（ssc-primalstinct:endgame，玩家实体附加）。
 * 死亡复制 returnAnchor（眷属实现10）：ALWAYS_COPY；运行中的转化会话由登录对账清理或续账。
 */
public class RegEndgameComponent implements EntityComponentInitializer {

    public static final ComponentKey<EndgamePlayerComponent> ENDGAME =
            ComponentRegistry.getOrCreate(
                    Identifier.of(SSCPrimalstinct.MOD_ID, "endgame"),
                    EndgamePlayerComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(ENDGAME, EndgamePlayerComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
