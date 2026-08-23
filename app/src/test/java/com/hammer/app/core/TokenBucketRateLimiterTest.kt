package com.hammer.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketRateLimiterTest {

    @Test
    fun `allows up to capacity immediately then blocks`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10, initialCapacity = 5, nanoClock = { now })

        repeat(5) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `refills tokens proportionally to elapsed time`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 10, initialCapacity = 10, nanoClock = { now })

        repeat(10) { limiter.tryAcquire() }
        assertFalse(limiter.tryAcquire())

        now += 500_000_000L // 0.5s at 10 req/s -> 5 tokens
        repeat(5) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `never exceeds bucket capacity even after a long idle period`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 100, initialCapacity = 5, nanoClock = { now })

        now += 60_000_000_000L // 60s idle, would be 6000 tokens without the cap
        repeat(5) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
    }

    @Test
    fun `updateRate changes the refill speed`() {
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 1, initialCapacity = 10, nanoClock = { now })

        repeat(10) { limiter.tryAcquire() }
        limiter.updateRate(100)

        now += 100_000_000L // 0.1s at 100 req/s -> 10 tokens
        assertEquals(100, limiter.currentRate())
        repeat(10) { assertTrue(limiter.tryAcquire()) }
    }

    @Test
    fun `updateRate grows capacity so a ramp can exceed its initial rate`() {
        // Regression test for the Ramp bug: a limiter started at 1 rps whose capacity never grew
        // could never sustain more than ~1 token per refill even after ramping the rate up.
        var now = 0L
        val limiter = TokenBucketRateLimiter(ratePerSecond = 1, nanoClock = { now })

        // Drain the single starting token.
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())

        // Ramp up to 500 rps and let a full second pass.
        limiter.updateRate(500)
        now += 1_000_000_000L

        // The bucket must now hold ~500 tokens, not stay clamped near 1.
        var acquired = 0
        while (limiter.tryAcquire()) acquired++
        assertTrue("expected ~500 tokens, got $acquired", acquired >= 400)
    }
}
