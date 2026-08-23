package com.hammer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hammer.app.R

@Composable
fun SettingsDialog(state: HammerUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_settings)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${stringResource(R.string.settings_concurrency)}: ${state.concurrency}")
                Slider(
                    value = state.concurrency.toFloat(),
                    onValueChange = { viewModel.updateConcurrency(it.toInt()) },
                    valueRange = 1f..200f
                )

                Text("${stringResource(R.string.settings_duration)}: ${state.durationSeconds}s")
                Slider(
                    value = state.durationSeconds.toFloat(),
                    onValueChange = { viewModel.updateDurationSeconds(it.toInt()) },
                    valueRange = 0f..300f
                )

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.settings_rate_limit))
                    Switch(checked = state.rateLimitEnabled, onCheckedChange = viewModel::updateRateLimitEnabled)
                }
                if (state.rateLimitEnabled) {
                    Text("${state.rateLimitRps} req/s")
                    Slider(
                        value = state.rateLimitRps.toFloat(),
                        onValueChange = { viewModel.updateRateLimitRps(it.toInt()) },
                        valueRange = 1f..1000f
                    )
                }

                Text("${stringResource(R.string.settings_timeout)}: ${state.timeoutMillis}ms")
                Slider(
                    value = state.timeoutMillis.toFloat(),
                    onValueChange = { viewModel.updateTimeoutMillis(it.toLong()) },
                    valueRange = 500f..30_000f
                )

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.settings_ignore_tls_errors), modifier = Modifier.weight(1f))
                    Checkbox(checked = state.ignoreTlsErrors, onCheckedChange = viewModel::updateIgnoreTlsErrors)
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.settings_screen_always_on), modifier = Modifier.weight(1f))
                    Checkbox(checked = state.keepScreenOn, onCheckedChange = viewModel::updateKeepScreenOn)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
