package com.hammer.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hammer.app.R

/** §6 garde-fou : écran récap (cible, mode, rate, durée) + bouton GO, obligatoire avant tout run. */
@Composable
fun ConfirmationDialog(
    state: HammerUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onUnlockWebsiteRate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.confirm_target, state.targetLabel))
                Text(stringResource(R.string.confirm_mode, "${state.protocol} / ${state.profile}"))
                Text(stringResource(R.string.confirm_rate, if (state.rateLimitEnabled) "${state.rateLimitRps} req/s" else "illimité"))
                Text(stringResource(R.string.confirm_duration, if (state.durationSeconds > 0) "${state.durationSeconds}s" else "jusqu'à STOP"))

                if (state.targetKind == TargetKind.WEBSITE && !state.websiteRateUnlocked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onUnlockWebsiteRate) {
                        Text(stringResource(R.string.confirm_unlock_website_rate, 20))
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.padding(4.dp)) {
                Button(onClick = onConfirm) { Text(stringResource(R.string.confirm_go)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.confirm_cancel)) }
        }
    )
}
