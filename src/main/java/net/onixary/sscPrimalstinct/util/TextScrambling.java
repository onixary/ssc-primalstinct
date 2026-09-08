package net.onixary.sscPrimalstinct.util;
/** Stable selection; vanilla obfuscated styling animates selected glyphs. */
public final class TextScrambling {
    private TextScrambling() {}
    public static boolean selected(int index, int codePoint, float chance) {
        if (chance <= 0 || Character.isWhitespace(codePoint)) return false;
        int hash = (index * 0x9e3779b9) ^ (codePoint * 0x85ebca6b);
        hash ^= hash >>> 16; hash *= 0x7feb352d; hash ^= hash >>> 15;
        return (hash & 0xffffff) / 16777216.0 < chance;
    }
}
