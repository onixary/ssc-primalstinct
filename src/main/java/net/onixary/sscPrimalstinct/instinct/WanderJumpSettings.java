package net.onixary.sscPrimalstinct.instinct;

/** Per-entity setting, applied only to unspawned wander proxies. */
public interface WanderJumpSettings {
    void primalstinct$setJumpHeightMultiplier(float multiplier);
    double primalstinct$consumeJumpVelocity();
}
