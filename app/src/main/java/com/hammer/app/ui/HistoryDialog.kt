package com.hammer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hammer.app.R

/** §7.2: derniers 10 runs, résumé rapide. */
@Composable
fun HistoryDialog(history: List<RunSummary>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_history)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (history.isEmpty()) {
                    Text("Aucun run pour l'instant.")
                } else {
                    history.forEach { summary ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(summary.targetLabel)
                            Text("moy: %.1f req/s — pic: %d req/s — échecs: %.1f%%".format(
                                summary.avgRps, summary.peakRps, summary.errorRatePercent
                            ))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
