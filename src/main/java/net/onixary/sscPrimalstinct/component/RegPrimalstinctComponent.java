package net.onixary.sscPrimalstinct.component;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 卡03：CCA key 为 ssc-primalstinct:primalstinct，玩家实体附加，ALWAYS_COPY
 * （死亡/重生保留进度；SSC 的手动复制流程不含本组件，避免复制两次副作用）。
 */
public class RegPrimalstinctComponent implements EntityComponentInitializer {

    public static final ComponentKey<PrimalstinctComponent> PRIMALSTINCT =
            ComponentRegistry.getOrCreate(
                    Identifier.of(SSCPrimalstinct.MOD_ID, "primalstinct"),
                    PrimalstinctComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(PRIMALSTINCT, PrimalstinctComponent::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
