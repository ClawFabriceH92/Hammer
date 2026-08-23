package com.hammer.app.core

class AutoStopWatcher(
    private val windowMillis: Long = 10_000L,
    private val failureThreshold: Double = 0.90,
    // Below this many samples in the window, a failure rate isn't statistically meaningful
    // (e.g. 1 failed request out of 1 is 100% but proves nothing) — avoids spurious trips at run start.
    private val minSamples: Int = 20,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onTrip: () -> Unit
) {
    private data class Sample(val timestampMillis: Long, val success: Boolean)

    private val samples = ArrayDeque<Sample>()
    private val lock = Any()
    private var tripped = false

    fun record(success: Boolean) {
        synchronized(lock) {
            if (tripped) return

            val now = clock()
            samples.addLast(Sample(now, success))
            while (samples.isNotEmpty() && now - samples.first().timestampMillis > windowMillis) {
                samples.removeFirst()
            }

            if (samples.size >= minSamples) {
                val failures = samples.count { !it.success }
                val failureRate = failures.toDouble() / samples.size
                if (failureRate > failureThreshold) {
                    tripped = true
                    onTrip()
                }
            }
        }
    }

    fun reset() = synchronized(lock) {
        samples.clear()
        tripped = false
    }

    fun isTripped(): Boolean = synchronized(lock) { tripped }
}
