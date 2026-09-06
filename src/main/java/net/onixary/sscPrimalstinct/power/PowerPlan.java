package net.onixary.sscPrimalstinct.power;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;

/**
 * 卡06：一次 reconcile 期望达到的能力终态。
 * grants：附属来源授予（powerId → addon sourceId ∈ {ssc-primalstinct:form_base, ssc-primalstinct:level_N}）；
 * maskedBaseline：需屏蔽的 SSC origin 基线 power（source 为当前形态 layer 的 origin，结算时解析）。
 */
public final class PowerPlan {

    public static final PowerPlan EMPTY = new PowerPlan(Map.of(), Set.of());

    public final Map<Identifier, Identifier> grants;
    public final Set<Identifier> maskedBaseline;

    public PowerPlan(Map<Identifier, Identifier> grants, Set<Identifier> maskedBaseline) {
        this.grants = Map.copyOf(grants);
        this.maskedBaseline = Set.copyOf(maskedBaseline);
    }
}
