package net.onixary.sscPrimalstinct;

import net.onixary.sscPrimalstinct.instinct.PrimalstinctLifecycle;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.sscPrimalstinct.command.PrimalstinctCommands;
import net.onixary.sscPrimalstinct.data.PrimalProfileReloadListener;
import net.onixary.sscPrimalstinct.data.PrimalRosterManager;
import net.onixary.sscPrimalstinct.instinct.PrimalstinctTicker;
import net.onixary.sscPrimalstinct.network.PrimalstinctNetwork;
import net.onixary.sscPrimalstinct.power.PrimalPowerReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSCPrimalstinct implements ModInitializer {

    public static final String MOD_ID = "ssc-primalstinct";
    public static final String SSC_MOD_ID = "shape-shifter-curse";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        net.onixary.sscPrimalstinct.effect.InstinctOverheatingEffect.register();
        boolean sscLoaded = FabricLoader.getInstance().isModLoaded(SSC_MOD_ID);
        if (!sscLoaded) {
            LOGGER.error("SSC (shape-shifter-curse) is missing; this addon requires it as a hard dependency.");
        }

        // 卡17：服务端配置（AutoConfig 注册即读文件；ModMenu 修改只落盘，会话值在 SERVER_STARTING 快照）
        net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            net.onixary.sscPrimalstinct.config.PrimalstinctServerConfig.snapshotActive();
            PrimalstinctLifecycle.clear();
            net.onixary.sscPrimalstinct.selection.SelectionSessionManager.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PrimalstinctLifecycle.clear();
            net.onixary.sscPrimalstinct.selection.SelectionSessionManager.clear();
        });

        // 卡02：形态名单数据包（解析→候选；SERVER_STARTED/热重载后引用校验→原子交换）
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new PrimalProfileReloadListener());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PrimalRosterManager.validateAndSwap();
            PrimalPowerReconciler.requestReconcileAll(server);
        });

        // 卡03：S2C 同步协议（事件钩子 + 限频校正）
        PrimalstinctNetwork.init();
        net.onixary.sscPrimalstinct.network.PerceptionSync.register();

        // 卡04：接管旧 InstinctUtils.serverTick 后的附属服务端 tick（卡05 填充内核）
        PrimalstinctTicker.register();

        // 卡14：物品注册（含创造模式标签页）
        net.onixary.sscPrimalstinct.items.RegPrimalstinctItems.registerAll();

        // 卡07：Apoli 工厂注册（速率 Power / 即时 Action / 三条件）
        net.onixary.sscPrimalstinct.power.PrimalstinctApoliFactories.register();

        // 卡09：库存锁槽生命周期（JOIN 种子规则 / 死亡掉落暂存 / 断线清理）
        net.onixary.sscPrimalstinct.inventory.InventoryLockManager.init();

        // 卡12：首次选择事务
        net.onixary.sscPrimalstinct.selection.SelectionPackets.registerServer();
        net.onixary.sscPrimalstinct.selection.SelectionSessionManager.init();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> net.onixary.sscPrimalstinct.selection.SelectionSessionManager.onPlayerReady(handler.player)));

        // 卡11：蜷缩/睡眠 C2S 接收器
        net.onixary.sscPrimalstinct.network.CurlSleepC2S.registerServer();
        net.onixary.sscPrimalstinct.network.WanderManualInputC2S.registerServer();
        net.onixary.sscPrimalstinct.sleep.CurlSleepController.init();

        // 卡11：切形态退出蜷缩
        SSCEvent.FORM_CHANGE_END.register((player, oldForm, newForm) -> {
            if (player instanceof ServerPlayerEntity sp) {
                net.onixary.sscPrimalstinct.sleep.CurlSleepController.wakeUp(sp, "form_change");
            }
        });

        // 卡15：形态切换后即时同步快照（managed 标记随名单形态变化，HUD 显隐不等待限频校正）
        SSCEvent.FORM_CHANGE_END.register((player, oldForm, newForm) -> {
            if (!player.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                PrimalstinctLifecycle.refresh(serverPlayer);
                PrimalstinctNetwork.syncNow(serverPlayer);
            }
        });

        // 卡10：交互限制规则生命周期
        net.onixary.sscPrimalstinct.interaction.InteractionRuleManager.init();

        // 卡06：统一 Power 挂载结算——事件合并为 tick 末主线程 reconcile
        SSCEvent.FORM_CHANGE_END.register((player, oldForm, newForm) -> {
            // _loadForm 双端触发；仅服务端结算（oldForm==newForm 的 reload 亦支持）
            if (!player.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                PrimalPowerReconciler.requestReconcile(serverPlayer);
            }
        });
        SSCEvent.ACCESSORY_EQUIP.register((player, itemID, pluginID) -> {
            if (!player.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                PrimalPowerReconciler.requestReconcile(serverPlayer);  // 饰品事件结束后（tick 末）再结算
            }
        });
        SSCEvent.ACCESSORY_UNEQUIP.register((player, itemID, pluginID) -> {
            if (!player.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                PrimalPowerReconciler.requestReconcile(serverPlayer);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PrimalPowerReconciler.requestReconcile(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                PrimalPowerReconciler.requestReconcile(newPlayer));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                PrimalRosterManager.validateAndSwap();
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    net.onixary.sscPrimalstinct.adapter.ssc.SSCAdapter.rebuildCurrentForm(player);
                    if (net.onixary.sscPrimalstinct.selection.SelectionSessionManager.isPending(player)) {
                        net.onixary.sscPrimalstinct.selection.SelectionSessionManager.onPlayerReady(player);
                    }
                }
                PrimalPowerReconciler.requestReconcileAll(server);  // 配置世代更新，强制重建附属来源
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(PrimalPowerReconciler::processPending);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PrimalstinctCommands.register(dispatcher));

        LOGGER.info("SSC Primalstinct initialized (SSC present: {})", sscLoaded);
    }

    /** 开发期判定（dev HUD 等使用；与 SSC 的同名约定一致）。 */
    public static boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
