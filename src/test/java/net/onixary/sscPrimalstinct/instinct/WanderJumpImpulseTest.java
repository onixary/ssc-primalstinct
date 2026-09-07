package net.onixary.sscPrimalstinct.instinct;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WanderJumpImpulseTest {
    @Test
    public void fallingTrajectoryIsUnchangedByContinuousAiPackets() {
        WanderJumpImpulse impulse = new WanderJumpImpulse();
        double vanilla = 0;
        double controlled = 0;
        for (int tick = 0; tick < 200; tick++) {
            impulse.accept(Double.NaN);
            controlled = impulse.consume(controlled, false);
            vanilla = (vanilla - 0.08) * 0.98;
            controlled = (controlled - 0.08) * 0.98;
            assertEquals(vanilla, controlled, 0);
        }
    }

    @Test
    public void jumpSurvivesPacketBatchAndIsAppliedOnlyOnce() {
        WanderJumpImpulse impulse = new WanderJumpImpulse();
        impulse.accept(0.6);
        impulse.accept(Double.NaN);
        assertEquals(0.6, impulse.consume(-0.0784, true), 0);
        assertEquals(0.5096, impulse.consume(0.5096, false), 0);
    }

    @Test
    public void lateAirborneJumpDoesNotRestartFallOrWaitForLanding() {
        WanderJumpImpulse impulse = new WanderJumpImpulse();
        impulse.accept(0.6);
        assertEquals(-0.4, impulse.consume(-0.4, false), 0);
        assertEquals(-0.0784, impulse.consume(-0.0784, true), 0);
    }

    @Test
    public void releaseDiscardsPendingJump() {
        WanderJumpImpulse impulse = new WanderJumpImpulse();
        impulse.accept(0.6);
        impulse.clear();
        assertEquals(-0.0784, impulse.consume(-0.0784, true), 0);
    }
}
