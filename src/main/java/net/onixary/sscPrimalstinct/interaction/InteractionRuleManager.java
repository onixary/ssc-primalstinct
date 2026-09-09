package net.onixary.sscPrimalstinct.interaction;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.sscPrimalstinct.power.factory.DropToolAfterUsePower;
import net.onixary.sscPrimalstinct.power.factory.PreventBlockPlacePower;
import net.onixary.sscPrimalstinct.power.factory.PreventDoorPower;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卡10：交互限制规则（Power 驱动，服务端权威）。
 * prevent_door / prevent_block_place（可选 item_tag 范围）/ drop_tool_after_use；
 * 装备/副手锁与 2×2 合成禁用分别并入 InventoryLockRule 与 ScreenHandler 守卫。
 * 由 PrimalPowerReconciler 在 Power 结算后统一更新。
 */
public final class InteractionRuleManager {

    public record InteractionRule(boolean preventDoor, boolean preventPlace,
                                  @Nullable Identifier placeItemTag, boolean dropToolAfterUse) {
        public boolean any() {
            return preventDoor || preventPlace || dropToolAfterUse;
        }
    }

    private static final Map<UUID, InteractionRule> RULES = new ConcurrentHashMap<>();

    private InteractionRuleManager() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> updateRule(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RULES.remove(handler.player.getUuid()));
    }

    /** Mixin 热路径：无任何交互限制返回 null。 */
    public static @Nullable InteractionRule ruleIfRestricted(@Nullable ServerPlayerEntity player) {
        if (player == null || !net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle.isManaged(player)) {
            return null;
        }
        InteractionRule rule = RULES.get(player.getUuid());
        return rule != null && rule.any() ? rule : null;
    }

    public static void updateRule(ServerPlayerEntity player) {
        boolean preventDoor = false;
        boolean preventPlace = false;
        Identifier placeTag = null;
        boolean dropTool = false;
        for (PreventDoorPower power : PowerHolderComponent.getPowers(player, PreventDoorPower.class)) {
            if (power.isActive()) {
                preventDoor = true;
            }
        }
        for (PreventBlockPlacePower power : PowerHolderComponent.getPowers(player, PreventBlockPlacePower.class)) {
            if (power.isActive() && power.prevents()) {
                preventPlace = true;
                if (power.getItemTag() != null) {
                    placeTag = power.getItemTag();
                }
            }
        }
        for (DropToolAfterUsePower power : PowerHolderComponent.getPowers(player, DropToolAfterUsePower.class)) {
            if (power.isActive()) {
                dropTool = true;
            }
        }
        RULES.put(player.getUuid(), new InteractionRule(preventDoor, preventPlace, placeTag, dropTool));
    }
}
