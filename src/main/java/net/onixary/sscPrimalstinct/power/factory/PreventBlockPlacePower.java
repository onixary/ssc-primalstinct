package net.onixary.sscPrimalstinct.power.factory;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import org.jetbrains.annotations.Nullable;

/**
 * 卡10：ssc-primalstinct:prevent_block_place —— 禁止放置方块（覆盖双手）。
 * item_tag 指定时只禁该物品标签内的方块物品；为空则禁所有 BlockItem 放置。
 * 只拦"放置"入口，不粗暴取消一切右键物品使用。
 * prevent=false 时不禁止放置，仅保留 on_place_action（成功放置时触发的实体动作）。
 */
public class PreventBlockPlacePower extends Power {

    private final @Nullable Identifier itemTag;
    private final boolean prevent;
    private final ActionFactory<Entity>.Instance onPlaceAction;

    public PreventBlockPlacePower(PowerType<?> type, LivingEntity entity, @Nullable Identifier itemTag,
                                  boolean prevent, ActionFactory<Entity>.Instance onPlaceAction) {
        super(type, entity);
        this.itemTag = itemTag;
        this.prevent = prevent;
        this.onPlaceAction = onPlaceAction;
    }

    public @Nullable Identifier getItemTag() {
        return itemTag;
    }

    public boolean prevents() {
        return prevent;
    }

    public @Nullable ActionFactory<Entity>.Instance getOnPlaceAction() {
        return onPlaceAction;
    }

    @SuppressWarnings("rawtypes")
    public static PowerFactory getFactory() {
        return new PowerFactory<>(
                Identifier.of(SSCPrimalstinct.MOD_ID, "prevent_block_place"),
                new SerializableData()
                        .add("item_tag", SerializableDataTypes.IDENTIFIER, null)
                        .add("prevent", SerializableDataTypes.BOOLEAN, true)
                        .add("on_place_action", ApoliDataTypes.ENTITY_ACTION, null),
                data -> (powerType, livingEntity) -> new PreventBlockPlacePower(
                        powerType, livingEntity,
                        data.isPresent("item_tag") ? data.getId("item_tag") : null,
                        data.getBoolean("prevent"),
                        data.get("on_place_action"))
        ).allowCondition();
    }
}
