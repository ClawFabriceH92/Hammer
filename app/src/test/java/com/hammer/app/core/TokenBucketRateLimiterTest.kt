package com.hammer.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketRateLimiterTest {

    @Test
    fun `allows up to capacity immediately then blocks`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10, capacity = 5, nanoClock = { now })

        repeat(5) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `refills tokens proportionally to elapsed time`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10, capacity = 10, nanoClock = { now })

        repeat(10) { limiter.tryAcquire() }
        assertFalse(limiter.tryAcquire())

        now += 500_000_000L // 0.5s at 10 req/s -> 5 tokens
        repeat(5) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `never exceeds bucket capacity even after a long idle period`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 100, capacity = 5, nanoClock = { now })

        now += 60_000_000_000L // 60s idle, would be 6000 tokens without the cap
        repeat(5) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `updateRate changes the refill speed`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 1, capacity = 10, nanoClock = { now })

        repeat(10) { limiter.tryAcquire() }
        limiter.updateRate(100)

        now += 100_000_000L // 0.1s at 100 req/s -> 10 tokens
        assertEquals(100, limiter.currentRate())
        repeat(10) { assertTrue(limiter.tryAcquire()) }
    }
}
