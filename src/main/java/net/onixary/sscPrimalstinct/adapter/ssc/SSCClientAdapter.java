package net.onixary.sscPrimalstinct.adapter.ssc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.particle.ParticleEffect;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.mana.IManaRender;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.ManaRegistriesClient;

/**
 * 卡15：SSC 客户端类的唯一引用点（adapter/ssc 集中引用原则的客户端侧延伸）。
 * 仅在物理客户端加载（依赖 @Environment(CLIENT) 的 ManaRegistriesClient）。
 */
@Environment(EnvType.CLIENT)
public final class SSCClientAdapter {

    private SSCClientAdapter() {
    }

    /**
     * 当前形态的法力条是否顶替旧本能条位置（InstinctBarLikeManaBar 等 OverrideInstinctBar 实现）。
     * 新本能条不参与 SSC 的顶替链：法力条照常渲染在原位，新本能条整体上移避让，
     * 保证有法力形态同时看到两类资源（白板卡15 验收项）。
     */
    public static boolean manaOverridesInstinctBar() {
        if (!SSCAdapter.isLoaded()) {
            return false;
        }
        IManaRender render = ManaRegistriesClient.getManaRender(ManaComponent.LocalManaTypeID);
        return render != null && render.OverrideInstinctBar();
    }

    /** 高值预警粒子：沿用旧 SSC 的变身粒子（StaticParams.PLAYER_TRANSFORM_PARTICLE）。 */
    public static ParticleEffect transformParticle() {
        return StaticParams.PLAYER_TRANSFORM_PARTICLE;
    }
}
