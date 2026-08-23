package com.hammer.app.stats

import com.hammer.app.engine.ErrorCategory
import com.hammer.app.engine.RequestOutcome
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

data class StatsSnapshot(
    val totalSent: Long,
    val totalOk: Long,
    val totalFailed: Long,
    val currentRps: Long,
    val peakRps: Long,
    val p50Millis: Long,
    val p95Millis: Long,
    val p99Millis: Long,
    val errorCounts: Map<ErrorCategory, Long>,
    val sparkline: List<Long>,
    val elapsedSeconds: Long
)

class StatsCollector(private val clock: () -> Long = System::currentTimeMillis) {
    private val startedAtMillis = clock()
    private val requestIdGenerator = AtomicLong(0)

    private val totalSent = LongAdder()
    private val totalOk = LongAdder()
    private val totalFailed = LongAdder()
    private val errorCounts = ConcurrentHashMap<ErrorCategory, LongAdder>()
    private val peakRps = AtomicLong(0)

    private val records = RequestRecordRingBuffer()
    private val rpsTracker = RequestsPerSecondTracker()

    fun record(outcome: RequestOutcome) {
        val id = requestIdGenerator.incrementAndGet()
        totalSent.increment()
        if (outcome.success) {
            totalOk.increment()
        } else {
            totalFailed.increment()
            errorCounts.getOrPut(outcome.errorCategory) { LongAdder() }.increment()
        }

        records.add(RequestRecord(id, clock(), outcome.success, outcome.latencyMillis, outcome.errorCategory))

        rpsTracker.record()
        peakRps.updateAndGet { current -> maxOf(current, rpsTracker.currentRate()) }
    }

    fun snapshot(): StatsSnapshot = StatsSnapshot(
        totalSent = totalSent.sum(),
        totalOk = totalOk.sum(),
        totalFailed = totalFailed.sum(),
        currentRps = rpsTracker.currentRate(),
        peakRps = peakRps.get(),
        p50Millis = records.percentileLatencyMillis(0.50),
        p95Millis = records.percentileLatencyMillis(0.95),
        p99Millis = records.percentileLatencyMillis(0.99),
        errorCounts = errorCounts.mapValues { it.value.sum() },
        sparkline = rpsTracker.sparkline(),
        elapsedSeconds = (clock() - startedAtMillis) / 1000
    )

    fun exportableRecords(): List<RequestRecord> = records.snapshotOldestToNewest()
}
