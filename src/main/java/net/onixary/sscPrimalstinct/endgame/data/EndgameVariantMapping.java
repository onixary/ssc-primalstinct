package net.onixary.sscPrimalstinct.endgame.data;

import net.minecraft.util.Identifier;

/**
 * 眷属实现02/12：单个源形态→原始变体的映射（variants/*.json 的解析结果）。
 * 不变量：source != target（无自映射）、target 必须是本模组注册的独立变体 FormID、
 * 映射链无循环（变体不再作为 source 出现）。
 */
public final class EndgameVariantMapping {

    public final Identifier sourceForm;
    public final Identifier targetForm;
    /** 变为该变体时弹出的对话 Info 翻译键（形态专属，眷属实现14）。 */
    public final String completionInfoKey;
    /** 回溯源文件，用于错误输出。 */
    public final String sourceFile;

    public EndgameVariantMapping(Identifier sourceForm, Identifier targetForm,
                                 String completionInfoKey, String sourceFile) {
        this.sourceForm = sourceForm;
        this.targetForm = targetForm;
        this.completionInfoKey = completionInfoKey;
        this.sourceFile = sourceFile;
    }
}
