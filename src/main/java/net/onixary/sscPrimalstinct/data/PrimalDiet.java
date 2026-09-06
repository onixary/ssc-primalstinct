package net.onixary.sscPrimalstinct.data;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 卡08：食性档案（data/&lt;ns&gt;/primalstinct/diets/*.json）。
 * 三档：normal（可吃且本能降低，恢复路径）、unsuitable（可吃但本能上升）、
 * forbidden（可吃但无营养——apoli:modify_food 置零，与 SSC 原版 raw_meat_only 同语义）。
 * 行为由引用这些标签的 Power 驱动（action_on_item_use / modify_food），
 * 本档案同时是数据契约与选择界面（卡13）展示数据源。
 */
public record PrimalDiet(
        Identifier id,
        float normalDelta,
        Identifier normalTag,
        float unsuitableDelta,
        Identifier unsuitableTag,
        @Nullable Identifier forbiddenTag,
        String sourceFile) {
}
