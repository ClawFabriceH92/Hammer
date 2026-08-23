package com.hammer.app.stats

import com.hammer.app.engine.ErrorCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

data class RequestRecord(
    val requestId: Long,
    val timestampMillis: Long,
    val success: Boolean,
    val latencyMillis: Long,
    val errorCategory: ErrorCategory
)

/**
 * Keeps the last [capacity] requests only (§7.1/§7.2, addendum 16.6): percentiles and the CSV
 * export both read from this sliding window, not the full run — on a long "Max" run the report
 * reflects the tail of the run, not its entirety.
 */
class RequestRecordRingBuffer(private val capacity: Int = 10_000) {
    private val buffer = arrayOfNulls<RequestRecord>(capacity)
    private var writeIndex = 0
    private var filledCount = 0
    private val lock = Any()

    fun add(record: RequestRecord) = synchronized(lock) {
        buffer[writeIndex] = record
        writeIndex = (writeIndex + 1) % capacity
        if (filledCount < capacity) filledCount++
    }

    fun snapshotOldestToNewest(): List<RequestRecord> = synchronized(lock) {
        orderedSnapshot()
    }

    fun percentileLatencyMillis(p: Double): Long = synchronized(lock) {
        if (filledCount == 0) return 0
        val latencies = orderedSnapshot().map { it.latencyMillis }.sorted()
        val index = (p * (latencies.size - 1)).toInt().coerceIn(0, latencies.size - 1)
        return latencies[index]
    }

    private fun orderedSnapshot(): List<RequestRecord> = if (filledCount < capacity) {
        buffer.take(filledCount).filterNotNull()
    } else {
        (buffer.drop(writeIndex) + buffer.take(writeIndex)).filterNotNull()
    }
}

/** Per-second request counts for the live req/s figure and the 60s sparkline (§7.1). */
class RequestsPerSecondTracker {
    private val perSecondCounts = ConcurrentHashMap<Long, LongAdder>()

    fun record(nowEpochSecond: Long = System.currentTimeMillis() / 1000) {
        perSecondCounts.getOrPut(nowEpochSecond) { LongAdder() }.increment()
        pruneOlderThan(nowEpochSecond - 60)
    }

    fun currentRate(nowEpochSecond: Long = System.currentTimeMillis() / 1000): Long =
        perSecondCounts[nowEpochSecond - 1]?.sum() ?: 0L

    fun sparkline(windowSeconds: Int = 60, nowEpochSecond: Long = System.currentTimeMillis() / 1000): List<Long> =
        ((nowEpochSecond - windowSeconds) until nowEpochSecond).map { second -> perSecondCounts[second]?.sum() ?: 0L }

    private fun pruneOlderThan(threshold: Long) {
        perSecondCounts.keys.removeIf { it < threshold }
    }
}
