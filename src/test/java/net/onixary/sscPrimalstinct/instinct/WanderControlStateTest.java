package net.onixary.sscPrimalstinct.instinct;

import org.junit.Test;
import static org.junit.Assert.*;

public class WanderControlStateTest {
    @Test
    public void ordinaryInputReleasesAndRejectsStalePackets() {
        var state = new WanderControlState();
        assertTrue(state.accept(true, false));
        assertTrue(state.manualInput());
        assertFalse(state.accept(true, false));
        assertFalse(state.isActive());
        state.accept(false, false);
        assertTrue(state.accept(true, false));
    }

    @Test
    public void overheatingOverridesAnInFlightRelease() {
        var state = new WanderControlState();
        state.accept(true, false);
        state.manualInput();
        assertTrue(state.accept(true, true));
        for (int tick = 0; tick < 200; tick++) {
            assertFalse(state.manualInput());
            assertTrue(state.isActive());
            assertTrue(state.isForced());
        }
    }

    @Test
    public void effectEndRestoresManualControl() {
        var state = new WanderControlState();
        state.accept(true, true);
        state.accept(false, false);
        assertFalse(state.isForced());
        assertFalse(state.isActive());
        assertTrue(state.manualInput());
        assertTrue(state.accept(true, false));
        assertTrue(state.manualInput());
    }

    @Test
    public void disconnectOrWatchdogClearCannotLeaveInputLocked() {
        var state = new WanderControlState();
        state.accept(true, true);
        state.clear();
        assertFalse(state.isForced());
        assertTrue(state.manualInput());
    }
}
