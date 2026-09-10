package net.onixary.sscPrimalstinct.adapter.ssc;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.ISubForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.sscPrimalstinct.SSCPrimalstinct;
import org.jetbrains.annotations.Nullable;

/**
 * SSC 内部类的唯一引用点（卡01：adapter/ssc 集中引用 SSC 内部类）。
 * 其余包不得直接 import net.onixary.shapeShifterCurseFabric.*，以便后续跟随 SSC 基线调整时只改这里。
 */
public final class SSCAdapter {

    private SSCAdapter() {
    }

    public static void invalidateInstinctRate(PlayerEntity player) {
        net.onixary.sscPrimalstinct.mixin.ssc.InstinctUtilsAccessor.primalstinct$rates().put(player.getUuid(), Float.NaN);
    }

    // ---- 表现复用：SSC 变形屏幕效果的时长常量与叠加层（满值锁定演出借用）----

    /** SSC 变形屏幕效果时长（tick）：入段（恶心渐强至黑屏）。 */
    public static int transformFxDurationIn() {
        return net.onixary.shapeShifterCurseFabric.data.StaticParams.TRANSFORM_FX_DURATION_IN;
    }

    /** SSC 变形屏幕效果时长（tick）：出段（黑屏渐退）。 */
    public static int transformFxDurationOut() {
        return net.onixary.shapeShifterCurseFabric.data.StaticParams.TRANSFORM_FX_DURATION_OUT;
    }

    public static void rebuildCurrentForm(PlayerEntity player) {
        var form = net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.getPlayerForm(player);
        net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils._loadForm(player, form);
    }

    public static void refreshFood(PlayerEntity player) {
        net.onixary.shapeShifterCurseFabric.util.CustomEdibleUtils.ReloadPlayerCustomEdible(player);
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(SSCPrimalstinct.SSC_MOD_ID);
    }

    /**
     * SSC 侧本能状态的只读快照，用于 debug 命令与后续的显示桥接（卡04）。
     */
    public record SscInstinctSnapshot(
            float instinctValue,
            float instinctRate,
            boolean instinctLock,
            @Nullable String formId) {
    }

    public static SscInstinctSnapshot readInstinct(PlayerEntity player) {
        PlayerFormComponent component = PlayerFormComponent.COMPONENT.get(player);
        boolean locked = InstinctUtils.playerInstinctLock.getOrDefault(player.getUuid(), false);
        String formId = component.nowFormID == null ? null : component.nowFormID.toString();
        return new SscInstinctSnapshot(component.instinctValue, component.instinctRate, locked, formId);
    }

    /**
     * 供早期联调用：确认 SSC 主类可用（不应抛出 NoClassDefFoundError，因为 fabric.mod.json 已硬依赖）。
     */
    public static String sscVersionTag() {
        return "SSC " + ShapeShifterCurseFabric.MOD_ID + " (project 1.10.0 baseline)";
    }

    /**
     * SSC 形态注册表的只读视图（卡02 名单引用校验用）。
     * 注意 ISubForm.getFormLayer 使用主形态 origin，FormID 与 OriginID 不能混为一谈。
     */
    public record SscFormInfo(Identifier id, boolean subForm, @Nullable Identifier masterFormId) {
    }

    public static @Nullable SscFormInfo formInfo(Identifier formId) {
        IForm form = RegPlayerForms.getPlayerForm(formId);
        if (form == null) {
            return null;
        }
        if (form instanceof ISubForm subForm) {
            Identifier master = subForm.getMasterForm() == null ? null : subForm.getMasterForm().getFormID();
            return new SscFormInfo(formId, true, master);
        }
        return new SscFormInfo(formId, false, null);
    }

    /** 玩家当前 FormID（卡06 reconcile 用）。 */
    public static @Nullable Identifier currentFormIdentifier(PlayerEntity player) {
        SscInstinctSnapshot snapshot = readInstinct(player);
        return snapshot.formId() == null ? null : Identifier.tryParse(snapshot.formId());
    }

    /** 形态 layer 的 origin source（getFormLayer().getRight()）——屏蔽 SSC 原能力时的删除 source。 */
    public static @Nullable Identifier formLayerSource(Identifier formId) {
        IForm form = RegPlayerForms.getPlayerForm(formId);
        return form == null ? null : form.getFormLayer().getRight();
    }

    /**
     * 眷属实现13：启动 SSC 变形演出（TransformManager.startTransform 的受控封装）。
     * 返回 false 表示 SSC isFormCanUse 拒绝或玩家已是目标形态；
     * SSC 配置 immediatelyTransform=true 时回调同步触发（onTargetFormApplied 幂等，可安全重入）。
     */
    public static boolean startPrimalTransformation(net.minecraft.server.network.ServerPlayerEntity player,
                                                    Identifier targetFormId, Runnable onComplete) {
        IForm targetForm = RegPlayerForms.getPlayerForm(targetFormId);
        if (targetForm == null) {
            SSCPrimalstinct.LOGGER.error("[primalstinct] 目标形态 {} 未注册，无法启动变形", targetFormId);
            return false;
        }
        return net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager.startTransform(
                player, targetForm, data -> {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }
}
