package net.onixary.sscPrimalstinct.instinct;

/** Entry and migration rules independent of Minecraft's lifecycle. */
public final class EntryPolicy {
    private EntryPolicy() {}
    public static boolean needsSelection(boolean chooseOnStart, boolean entryHandled, boolean selected) {
        return chooseOnStart && !entryHandled && !selected;
    }
    public static boolean legacyInitialized(boolean selected, float value, boolean locked, boolean hasStash) {
        return selected || value != 0 || locked || hasStash;
    }
}
