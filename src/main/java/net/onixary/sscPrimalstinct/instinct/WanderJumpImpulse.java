package net.onixary.sscPrimalstinct.instinct;

/** One-shot jump command; ordinary AI updates never replace the player's vertical motion. */
public final class WanderJumpImpulse {
    private double pending = Double.NaN;

    public void accept(double jump) {
        if (Double.isFinite(jump) && jump > 0) pending = jump;
    }

    public double consume(double currentVelocity, boolean onGround) {
        double result = onGround && Double.isFinite(pending) ? pending : currentVelocity;
        clear();
        return result;
    }

    public void clear() {
        pending = Double.NaN;
    }
}
