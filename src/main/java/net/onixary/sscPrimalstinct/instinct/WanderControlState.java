package net.onixary.sscPrimalstinct.instinct;

/** Client control ownership, independent of movement physics. */
public final class WanderControlState {
    private boolean active;
    private boolean forced;
    private boolean waitingForRelease;

    public boolean accept(boolean newActive, boolean newForced) {
        if (!newActive) {
            clear();
            return false;
        }
        if (newForced) waitingForRelease = false;
        if (waitingForRelease) return false;
        active = true;
        forced = newForced;
        return true;
    }

    /** Returns true when manual input is allowed to release movement. */
    public boolean manualInput() {
        if (isForced()) return false;
        waitingForRelease |= active;
        active = false;
        return true;
    }

    public void clear() {
        active = forced = waitingForRelease = false;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isForced() {
        return active && forced;
    }
}
