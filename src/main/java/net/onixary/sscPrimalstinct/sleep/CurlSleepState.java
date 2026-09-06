package net.onixary.sscPrimalstinct.sleep;

/** Synced state, separate from vanilla's sleeping position (which means actual sleep). */
public interface CurlSleepState {
    boolean primalstinct$isCurled();
    void primalstinct$setCurled(boolean curled);
    void primalstinct$resetSleepTimer();
}
