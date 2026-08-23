package com.hammer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hammer.app.R
import com.hammer.app.MainViewModel
import com.hammer.app.ui.theme.HammerBackground
import com.hammer.app.ui.theme.HammerBorder
import com.hammer.app.ui.theme.HammerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HammerScreen(state: HammerUiState, viewModel: MainViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = HammerBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            Card {
                Text(stringResource(R.string.target_label), fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SegmentedButton(
                        selected = state.targetKind == TargetKind.LOCAL_IP,
                        onClick = { viewModel.updateTargetKind(TargetKind.LOCAL_IP) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(stringResource(R.string.target_local_ip)) }
                    SegmentedButton(
                        selected = state.targetKind == TargetKind.WEBSITE,
                        onClick = { viewModel.updateTargetKind(TargetKind.WEBSITE) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(stringResource(R.string.target_website)) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.targetKind == TargetKind.LOCAL_IP) {
                    OutlinedTextField(
                        value = state.ipInput,
                        onValueChange = viewModel::updateIpInput,
                        label = { Text("192.168.0.20") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = state.websiteInput,
                        onValueChange = viewModel::updateWebsiteInput,
                        label = { Text("exemple.fr") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = state.port,
                        onValueChange = viewModel::updatePort,
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.path,
                        onValueChange = viewModel::updatePath,
                        label = { Text("Path") },
                        modifier = Modifier.weight(2f)
                    )
                }
            }

            Card {
                Text(stringResource(R.string.protocol_label), fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val protocols = listOf(
                        ProtocolChoice.HTTP_GET to R.string.protocol_http_get,
                        ProtocolChoice.HTTP_POST to R.string.protocol_http_post,
                        ProtocolChoice.TCP_RAW to R.string.protocol_tcp_raw
                    )
                    protocols.forEachIndexed { index, (choice, label) ->
                        val disabled = choice == ProtocolChoice.TCP_RAW && state.targetKind == TargetKind.WEBSITE
                        SegmentedButton(
                            selected = state.protocol == choice,
                            onClick = { viewModel.updateProtocol(choice) },
                            enabled = !disabled,
                            shape = SegmentedButtonDefaults.itemShape(index, protocols.size)
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            Card {
                Text(stringResource(R.string.profile_label), fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val profiles = listOf(
                        ProfileChoice.CONSTANT to R.string.profile_constant,
                        ProfileChoice.RAMP to R.string.profile_ramp,
                        ProfileChoice.BURST to R.string.profile_burst,
                        ProfileChoice.MAX to R.string.profile_max
                    )
                    profiles.forEachIndexed { index, (choice, label) ->
                        SegmentedButton(
                            selected = state.profile == choice,
                            onClick = { viewModel.updateProfile(choice) },
                            shape = SegmentedButtonDefaults.itemShape(index, profiles.size)
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            Card {
                StatsPanel(state)
            }

            state.validationError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { if (state.runState == RunState.RUNNING) viewModel.stopRun() else viewModel.requestStart() },
                    modifier = Modifier.weight(1f)
                ) {
                    // Emoji prefixed here rather than in strings.xml: astral-plane emoji crash aapt2 (see strings.xml).
                    Text(
                        if (state.runState == RunState.RUNNING) {
                            "⏹ ${stringResource(R.string.button_stop)}"
                        } else {
                            "▶ ${stringResource(R.string.button_start)}"
                        }
                    )
                }
                OutlinedButton(onClick = viewModel::applyFriendlyMode) {
                    Text(stringResource(R.string.button_friendly))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = viewModel::exportCurrentRun,
                    enabled = state.stats != null,
                    modifier = Modifier.weight(1f)
                ) { Text("📋 ${stringResource(R.string.button_export)}") }
                OutlinedButton(onClick = { viewModel.toggleSettings(true) }, modifier = Modifier.weight(1f)) {
                    Text("⚙ ${stringResource(R.string.button_settings)}")
                }
                OutlinedButton(onClick = { viewModel.toggleHistory(true) }, modifier = Modifier.weight(1f)) {
                    Text("📚 ${stringResource(R.string.button_history)}")
                }
            }
        }
    }

    if (state.runState == RunState.AWAITING_CONFIRMATION) {
        ConfirmationDialog(
            state = state,
            onConfirm = viewModel::confirmAndGo,
            onCancel = viewModel::cancelConfirmation,
            onUnlockWebsiteRate = viewModel::unlockWebsiteRate
        )
    }

    if (state.showSettings) {
        SettingsDialog(state = state, viewModel = viewModel, onDismiss = { viewModel.toggleSettings(false) })
    }

    if (state.showHistory) {
        HistoryDialog(history = state.history, onDismiss = { viewModel.toggleHistory(false) })
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HammerCard, RoundedCornerShape(8.dp))
            .border(1.dp, HammerBorder, RoundedCornerShape(8.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun StatsPanel(state: HammerUiState) {
    val stats = state.stats
    Text("${stringResource(R.string.stats_req_per_sec)}: ${stats?.currentRps ?: 0}", style = MaterialTheme.typography.headlineLarge)
    Sparkline(values = stats?.sparkline ?: emptyList())
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${stringResource(R.string.stats_total_sent)}: ${stats?.totalSent ?: 0}")
        Text("${stringResource(R.string.stats_total_ok)}: ${stats?.totalOk ?: 0}")
        Text("${stringResource(R.string.stats_total_failed)}: ${stats?.totalFailed ?: 0}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${stringResource(R.string.stats_latency_p50)}: ${stats?.p50Millis ?: 0}ms")
        Text("${stringResource(R.string.stats_latency_p95)}: ${stats?.p95Millis ?: 0}ms")
        Text("${stringResource(R.string.stats_latency_p99)}: ${stats?.p99Millis ?: 0}ms")
    }
}
