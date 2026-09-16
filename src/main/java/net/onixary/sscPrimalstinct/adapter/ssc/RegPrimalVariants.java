package net.onixary.sscPrimalstinct.adapter.ssc;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Ocelot3;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;

/**
 * 眷属实现12（A 阶段：豹猫）：SSC 独立原始变体形态注册。
 * 注册为独立 FormID（ssc-primalstinct:primal_ocelot），不是复用 master origin 的 ISubForm。
 * 初版按设计稿“完全复制一份原形态的动画/模型/power”：
 * 类与动画控制器直接复用 Form_Ocelot3，旗标/体型/缩放逐项对齐 OCELOT_3 注册参数；
 * getFormLayer 随 NormalForm 规则派生为 ssc-primalstinct:form_primal_ocelot
 * （origin 由数据包 origins/form_primal_ocelot.json 提供，渲染由 ssc_form_model JSON 提供）。
 * 注册在主入口最先执行（早于名单/终局配置的引用校验）。
 */
public final class RegPrimalVariants {

    public static final Identifier PRIMAL_OCELOT = Identifier.of(SSCPrimalstinct.MOD_ID, "primal_ocelot");

    private RegPrimalVariants() {
    }

    public static void registerAll() {
        if (RegPlayerForms.getPlayerForm(PRIMAL_OCELOT) != null) {
            SSCPrimalstinct.LOGGER.warn("[primalstinct] 原始变体形态 {} 已注册，跳过", PRIMAL_OCELOT);
            return;
        }
        RegPlayerForms.registerPlayerForm(new Form_Ocelot3(PRIMAL_OCELOT)
                .formFlag(FormUtils.FinalForm, FormUtils.InhibitorImmune,
                        FormUtils.NoInstinct, FormUtils.NoCursedMoonEffect)
                .bodyType(PlayerFormBodyType.FERAL)
                .applyScale(0.75f, 0.6f));
        SSCPrimalstinct.LOGGER.info("[primalstinct] 原始变体形态已注册：{}", PRIMAL_OCELOT);
        // 其余名单形态（snow_fox_3 / familiar_fox_3 / anubis_wolf_3）在 D 里程碑（眷属实现12 全形态覆盖）追加
    }
}
