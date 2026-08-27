package com.hammer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hammer.app.R
import com.hammer.app.MainViewModel
import com.hammer.app.ui.theme.HammerBackground
import com.hammer.app.ui.theme.HammerBorder
import com.hammer.app.ui.theme.HammerCard
import com.hammer.app.ui.theme.HammerTextSecondary

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
                    Segment(0, 2, state.targetKind == TargetKind.LOCAL_IP, { viewModel.updateTargetKind(TargetKind.LOCAL_IP) }, stringResource(R.string.target_local_ip))
                    Segment(1, 2, state.targetKind == TargetKind.WEBSITE, { viewModel.updateTargetKind(TargetKind.WEBSITE) }, stringResource(R.string.target_website))
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.targetKind == TargetKind.LOCAL_IP) {
                    OutlinedTextField(
                        value = state.ipInput,
                        onValueChange = viewModel::updateIpInput,
                        label = { Text("192.168.0.20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = state.websiteInput,
                        onValueChange = viewModel::updateWebsiteInput,
                        label = { Text("exemple.fr") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = state.port,
                        onValueChange = viewModel::updatePort,
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.path,
                        onValueChange = viewModel::updatePath,
                        label = { Text("Path") },
                        singleLine = true,
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
                        Segment(
                            index = index,
                            count = protocols.size,
                            selected = state.protocol == choice,
                            onClick = { viewModel.updateProtocol(choice) },
                            label = stringResource(label),
                            enabled = !disabled
                        )
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
                        Segment(
                            index = index,
                            count = profiles.size,
                            selected = state.profile == choice,
                            onClick = { viewModel.updateProfile(choice) },
                            label = stringResource(label)
                        )
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
                        text = if (state.runState == RunState.RUNNING) {
                            "⏹ ${stringResource(R.string.button_stop)}"
                        } else {
                            "▶ ${stringResource(R.string.button_start)}"
                        },
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = viewModel::applyFriendlyMode,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.button_friendly), maxLines = 1)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(stringResource(R.string.button_export), Modifier.weight(1f), enabled = state.stats != null) { viewModel.exportCurrentRun() }
                ActionButton(stringResource(R.string.button_settings), Modifier.weight(1f)) { viewModel.toggleSettings(true) }
                ActionButton(stringResource(R.string.button_history), Modifier.weight(1f)) { viewModel.toggleHistory(true) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.Segment(
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = SegmentedButtonDefaults.itemShape(index, count),
        // Drop the reserved leading check-icon slot so long labels ("Constante", "HTTP POST")
        // have the full segment width and don't wrap or truncate.
        icon = {},
        label = {
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                // labelSmall (11sp) guarantees the longest 4-segment label ("Constante") fits
                // without wrapping or ellipsis on a narrow phone.
                style = MaterialTheme.typography.labelSmall
            )
        }
    )
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelMedium
        )
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
    Text(
        "${stringResource(R.string.stats_req_per_sec)}: ${stats?.currentRps ?: 0}",
        style = MaterialTheme.typography.headlineLarge,
        maxLines = 1
    )
    Sparkline(values = stats?.sparkline ?: emptyList())
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCell(stringResource(R.string.stats_total_sent), "${stats?.totalSent ?: 0}", Modifier.weight(1f))
        StatCell(stringResource(R.string.stats_total_ok), "${stats?.totalOk ?: 0}", Modifier.weight(1f))
        StatCell(stringResource(R.string.stats_total_failed), "${stats?.totalFailed ?: 0}", Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCell(stringResource(R.string.stats_latency_p50), "${stats?.p50Millis ?: 0} ms", Modifier.weight(1f))
        StatCell(stringResource(R.string.stats_latency_p95), "${stats?.p95Millis ?: 0} ms", Modifier.weight(1f))
        StatCell(stringResource(R.string.stats_latency_p99), "${stats?.p99Millis ?: 0} ms", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HammerTextSecondary,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
