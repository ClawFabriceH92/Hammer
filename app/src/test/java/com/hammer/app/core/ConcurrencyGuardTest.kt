package com.hammer.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrencyGuardTest {

    @Test
    fun `clamps requested concurrency to the hard cap without debug flag`() {
        val guard = ConcurrencyGuard(requestedConcurrency = 10_000, debugFlagEnabled = false)
        assertEquals(ConcurrencyGuard.HARD_CAP, guard.effectiveConcurrency)
    }

    @Test
    fun `keeps requested concurrency when under the hard cap`() {
        val guard = ConcurrencyGuard(requestedConcurrency = 200, debugFlagEnabled = false)
        assertEquals(200, guard.effectiveConcurrency)
    }

    @Test
    fun `never allows zero or negative concurrency`() {
        val guard = ConcurrencyGuard(requestedConcurrency = -5, debugFlagEnabled = false)
        assertEquals(1, guard.effectiveConcurrency)
    }

    @Test
    fun `debug flag allows exceeding the hard cap`() {
        val guard = ConcurrencyGuard(requestedConcurrency = 10_000, debugFlagEnabled = true)
        assertEquals(10_000, guard.effectiveConcurrency)
    }

    @Test
    fun `semaphore enforces the effective concurrency at runtime`() {
        val guard = ConcurrencyGuard(requestedConcurrency = 2, debugFlagEnabled = false)
        assertTrue(guard.tryAcquire())
        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())

        guard.release()
        assertTrue(guard.tryAcquire())
    }
}
