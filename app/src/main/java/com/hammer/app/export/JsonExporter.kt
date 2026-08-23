package com.hammer.app.export

import com.hammer.app.stats.StatsSnapshot
import java.io.OutputStream

/** Writes the aggregated stats (§7.2 "Export JSON : stats agrégées") as plain JSON, no library needed. */
object JsonExporter {
    fun write(snapshot: StatsSnapshot, out: OutputStream) {
        val json = buildString {
            append('{')
            append("\"totalSent\":${snapshot.totalSent},")
            append("\"totalOk\":${snapshot.totalOk},")
            append("\"totalFailed\":${snapshot.totalFailed},")
            append("\"currentRps\":${snapshot.currentRps},")
            append("\"peakRps\":${snapshot.peakRps},")
            append("\"elapsedSeconds\":${snapshot.elapsedSeconds},")
            append("\"latencyMillis\":{\"p50\":${snapshot.p50Millis},\"p95\":${snapshot.p95Millis},\"p99\":${snapshot.p99Millis}},")
            append("\"errorCounts\":{")
            append(snapshot.errorCounts.entries.joinToString(",") { (category, count) -> "\"${category.name}\":$count" })
            append("},")
            append("\"sparkline\":[${snapshot.sparkline.joinToString(",")}]")
            append('}')
        }
        out.bufferedWriter().use { it.write(json) }
    }
}
