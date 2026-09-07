package net.onixary.sscPrimalstinct.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import me.shedaniel.autoconfig.ConfigData.ValidationException;

/**
 * 卡15：本能条客户端配置（config/ssc-primalstinct-client.json，SSC 同款 AutoConfig + ModMenu）。
 * 锚点 1–9 与 SSC UIPositionUtils 的九宫格一致；默认值对齐 SSC 旧本能条默认位
 * （posType=8 中下锚 + (100, -9)），保持“原条位置”的迁移体验。
 * 用户 offset 配置用于与法力/蛛丝/护甲/饥饿条共存时手工避让（法力条在场时另有 -14 自动上移）。
 * holder 保存时原地更新实例（引用不变），HUD 实时生效。
 */
@Environment(EnvType.CLIENT)
@Config(name = "ssc-primalstinct-client")
public class PrimalstinctClientConfig implements me.shedaniel.autoconfig.ConfigData {

    @ConfigEntry.Category("General")
    @ConfigEntry.BoundedDiscrete(min = 1, max = 9)
    @Comment("Instinct bar anchor position on the 3x3 screen grid (same grid as SSC bar config). Default: 8 (bottom center)")
    public int barPosType = 8;

    @ConfigEntry.Category("General")
    @Comment("Instinct bar X offset from the anchor. Default: 100")
    public int barOffsetX = 100;

    @ConfigEntry.Category("General")
    @Comment("Instinct bar Y offset from the anchor. Default: -9")
    public int barOffsetY = -9;

    /** holder 实例（保存时原地更新，HUD 引用实时生效）。 */
    private static PrimalstinctClientConfig active = new PrimalstinctClientConfig();

    public static PrimalstinctClientConfig get() {
        return active;
    }

    /** onInitializeClient 注册并读取文件（原 Gson 手工读写由 AutoConfig 接管，文件名/格式不变）。 */
    public static void register() {
        AutoConfig.register(PrimalstinctClientConfig.class, GsonConfigSerializer::new);
        active = AutoConfig.getConfigHolder(PrimalstinctClientConfig.class).getConfig();
    }

    /** 加载/保存后的钳制（越界值就地收敛而非报错；cloth 11 的回调为 validatePostLoad）。 */
    @Override
    public void validatePostLoad() throws ValidationException {
        if (barPosType < 1 || barPosType > 9) {
            barPosType = 8;
        }
        barOffsetX = Math.max(-2000, Math.min(2000, barOffsetX));
        barOffsetY = Math.max(-2000, Math.min(2000, barOffsetY));
    }
}
