package com.hammer.app.core

/**
 * Token bucket used to pace requests to a target rate. The bucket refills continuously at
 * [ratePerSecond] and holds at most [capacity] tokens (one second of burst by default).
 *
 * [updateRate] also grows the capacity to match the new rate. This matters for the Ramp profile,
 * which starts the limiter at a low rate and repeatedly raises it: without growing the capacity the
 * bucket would stay clamped to its initial (tiny) size and the ramp could never exceed roughly one
 * token per refill, no matter how high the configured target rate.
 */
class TokenBucketRateLimiter(
    ratePerSecond: Int,
    initialCapacity: Int = ratePerSecond,
    private val nanoClock: () -> Long = System::nanoTime
) {
    @Volatile
    private var ratePerSecond: Int = ratePerSecond.coerceAtLeast(1)

    private var capacity: Double = initialCapacity.coerceAtLeast(1).toDouble()
    private var tokens: Double = capacity
    private var lastRefillNanos: Long = nanoClock()
    private val lock = Any()

    fun tryAcquire(): Boolean = synchronized(lock) {
        refillLocked()
        if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else {
            false
        }
    }

    fun updateRate(newRatePerSecond: Int) = synchronized(lock) {
        refillLocked()
        val clamped = newRatePerSecond.coerceAtLeast(1)
        ratePerSecond = clamped
        // Grow the bucket so the higher rate is actually reachable; never shrink it below the
        // tokens already accumulated so a rate change never silently drops in-flight capacity.
        capacity = maxOf(capacity, clamped.toDouble())
    }

    fun currentRate(): Int = ratePerSecond

    private fun refillLocked() {
        val now = nanoClock()
        val elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0
        lastRefillNanos = now
        tokens = minOf(capacity, tokens + elapsedSeconds * ratePerSecond)
    }
}
