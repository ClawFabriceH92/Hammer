package com.hammer.app.core

class TokenBucketRateLimiter(
    ratePerSecond: Int,
    private val capacity: Int = ratePerSecond,
    private val nanoClock: () -> Long = System::nanoTime
) {
    @Volatile
    private var ratePerSecond: Int = ratePerSecond

    private var tokens: Double = capacity.toDouble()
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
        ratePerSecond = newRatePerSecond
    }

    fun currentRate(): Int = ratePerSecond

    private fun refillLocked() {
        val now = nanoClock()
        val elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0
        lastRefillNanos = now
        tokens = minOf(capacity.toDouble(), tokens + elapsedSeconds * ratePerSecond)
    }
}
