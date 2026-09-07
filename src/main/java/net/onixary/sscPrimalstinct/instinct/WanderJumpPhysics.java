package net.onixary.sscPrimalstinct.instinct;

/** Vanilla dry-land jump approximation: move, gravity, then vertical drag. */
public final class WanderJumpPhysics {
    private WanderJumpPhysics() {
    }

    public static float scaleHeight(float velocity, float multiplier) {
        if (velocity <= 0 || !Float.isFinite(velocity)
                || multiplier == 1 || multiplier <= 0 || !Float.isFinite(multiplier)) {
            return velocity;
        }
        double target = height(velocity) * multiplier;
        double low = 0;
        double high = Math.max(velocity, velocity * (double) multiplier);
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) * 0.5;
            if (height(mid) < target) low = mid;
            else high = mid;
        }
        return (float) ((low + high) * 0.5);
    }

    static double height(double velocity) {
        double height = 0;
        // Closed form avoids simulating long jumps for large configured multipliers.
        double drag = 0.98;
        double terminal = 0.08 * drag / (1 - drag);
        double ticks = Math.ceil(Math.log(terminal / (velocity + terminal)) / Math.log(drag));
        if (ticks <= 0) return height;
        return (velocity + terminal) * (1 - Math.pow(drag, ticks)) / (1 - drag) - terminal * ticks;
    }
}
