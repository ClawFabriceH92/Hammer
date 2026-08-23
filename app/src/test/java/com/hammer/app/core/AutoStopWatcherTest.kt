package com.hammer.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStopWatcherTest {

    @Test
    fun `does not trip below the minimum sample count even at 100 percent failure`() {
        var now = 0L
        var tripped = false
        val watcher = AutoStopWatcher(
            windowMillis = 10_000L,
            minSamples = 20,
            clock = { now },
            onTrip = { tripped = true }
        )

        repeat(19) { watcher.record(success = false) }
        assertFalse(tripped)
    }

    @Test
    fun `trips once failure rate exceeds 90 percent with enough samples`() {
        var now = 0L
        var tripped = false
        val watcher = AutoStopWatcher(
            windowMillis = 10_000L,
            minSamples = 20,
            clock = { now },
            onTrip = { tripped = true }
        )

        repeat(19) { watcher.record(success = true) }
        assertFalse(tripped)

        now += 11_000L // slide the window past that first batch so it doesn't drag the ratio down forever
        repeat(19) { watcher.record(success = true) }
        assertFalse(tripped) // still below minSamples once the stale batch is pruned

        watcher.record(success = false)
        assertFalse(tripped) // 1/20 = 5% failure, well under threshold

        repeat(199) { watcher.record(success = false) } // 200/219 ~= 91% failure
        assertTrue(tripped)
    }

    @Test
    fun `does not trip when failure rate stays at or below 90 percent`() {
        var now = 0L
        var tripped = false
        val watcher = AutoStopWatcher(
            windowMillis = 10_000L,
            minSamples = 20,
            clock = { now },
            onTrip = { tripped = true }
        )

        repeat(2) { watcher.record(success = true) }
        repeat(18) { watcher.record(success = false) } // exactly 90% failure, not strictly above
        assertFalse(tripped)
    }

    @Test
    fun `old samples fall out of the sliding window`() {
        var now = 0L
        var tripped = false
        val watcher = AutoStopWatcher(
            windowMillis = 10_000L,
            minSamples = 20,
            clock = { now },
            onTrip = { tripped = true }
        )

        repeat(20) { watcher.record(success = false) }
        assertTrue(tripped)

        watcher.reset()
        tripped = false
        now += 20_000L
        repeat(19) { watcher.record(success = true) }
        assertFalse(tripped)
    }

    @Test
    fun `stops evaluating once tripped`() {
        var now = 0L
        var tripCount = 0
        val watcher = AutoStopWatcher(
            windowMillis = 10_000L,
            minSamples = 20,
            clock = { now },
            onTrip = { tripCount++ }
        )

        repeat(30) { watcher.record(success = false) }
        assertTrue(tripCount == 1)
    }
}
