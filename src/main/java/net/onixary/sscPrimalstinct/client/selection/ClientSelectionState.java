package net.onixary.sscPrimalstinct.client.selection;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 卡13：客户端选择会话数据（全部来自服务端 S2C）。
 */
public final class ClientSelectionState {

    public static class SelectionList {
        public final List<Identifier> forms;
        public final int revision;
        public final long nonce;
        public final Identifier defaultForm;

        public SelectionList(List<Identifier> forms, int revision, long nonce, Identifier defaultForm) {
            this.forms = forms;
            this.revision = revision;
            this.nonce = nonce;
            this.defaultForm = defaultForm;
        }
    }

    private static volatile @Nullable SelectionList current;
    private static volatile boolean confirmed = false;
    private static volatile @Nullable String lastError = null;

    private ClientSelectionState() {
    }

    public static void accept(SelectionList list) {
        current = list;
        confirmed = false;
        lastError = null;
    }

    public static @Nullable SelectionList current() {
        return current;
    }

    public static void onConfirmed() {
        confirmed = true;
        current = null;
    }

    public static boolean isConfirmed() {
        return confirmed;
    }

    public static void setError(@Nullable String error) {
        lastError = error;
    }

    public static @Nullable String getLastError() {
        return lastError;
    }

    public static void clear() {
        current = null;
        confirmed = false;
        lastError = null;
    }
}
