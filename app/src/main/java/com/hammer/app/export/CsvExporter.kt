package com.hammer.app.export

import com.hammer.app.stats.RequestRecord
import java.io.OutputStream

/** Writes the `timestamp ; req_id ; ok ; latence_ms ; code_erreur` CSV described in §7.2. */
object CsvExporter {
    private const val HEADER = "timestamp;req_id;ok;latence_ms;code_erreur"

    fun write(records: List<RequestRecord>, out: OutputStream) {
        out.bufferedWriter().use { writer ->
            writer.write(HEADER)
            writer.newLine()
            for (record in records) {
                writer.write(
                    "${record.timestampMillis};${record.requestId};${record.success};" +
                        "${record.latencyMillis};${record.errorCategory.name}"
                )
                writer.newLine()
            }
        }
    }
}
