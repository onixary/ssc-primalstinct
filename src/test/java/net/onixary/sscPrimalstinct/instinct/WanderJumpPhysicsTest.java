package net.onixary.sscPrimalstinct.instinct;

import org.junit.Test;
import static org.junit.Assert.*;

public class WanderJumpPhysicsTest {
    private static double simulateHeight(double velocity) {
        double height = 0;
        while (velocity > 0) {
            height += velocity;
            velocity = (velocity - 0.08) * 0.98;
        }
        return height;
    }

    @Test
    public void doublesHeightAcrossDifferentBaseJumps() {
        for (float velocity : new float[]{0.21f, 0.42f, 0.52f, 0.62f}) {
            float scaled = WanderJumpPhysics.scaleHeight(velocity, 2);
            assertEquals(simulateHeight(velocity) * 2, simulateHeight(scaled), 0.00001);
            assertTrue(scaled < velocity * 2);
        }
    }

    @Test
    public void defaultPreservesVelocityExactly() {
        assertEquals(0.42f, WanderJumpPhysics.scaleHeight(0.42f, 1), 0);
    }

    @Test
    public void invalidMultiplierDoesNotChangeJump() {
        for (float multiplier : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(0.42f, WanderJumpPhysics.scaleHeight(0.42f, multiplier), 0);
        }
    }
}
