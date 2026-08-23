package com.hammer.app.ui

import com.hammer.app.engine.PacketPattern
import com.hammer.app.stats.StatsSnapshot

enum class TargetKind { LOCAL_IP, WEBSITE }
enum class ProtocolChoice { HTTP_GET, HTTP_POST, TCP_RAW }
enum class ProfileChoice { CONSTANT, RAMP, BURST, MAX }

enum class RunState { IDLE, COOLDOWN, AWAITING_CONFIRMATION, RUNNING, FINISHED }

data class RunSummary(
    val timestampMillis: Long,
    val targetLabel: String,
    val avgRps: Double,
    val peakRps: Long,
    val errorRatePercent: Double
)

data class HammerUiState(
    val targetKind: TargetKind = TargetKind.LOCAL_IP,
    val ipInput: String = "",
    val websiteInput: String = "",
    val port: String = "80",
    val path: String = "/",
    val protocol: ProtocolChoice = ProtocolChoice.HTTP_GET,
    val profile: ProfileChoice = ProfileChoice.CONSTANT,
    val concurrency: Int = 50,
    val durationSeconds: Int = 30,
    val rateLimitEnabled: Boolean = true,
    val rateLimitRps: Int = 50,
    val timeoutMillis: Long = 5_000,
    val tcpPacketSizeBytes: Int = 512,
    val tcpPacketPattern: PacketPattern = PacketPattern.ZERO,
    val ignoreTlsErrors: Boolean = false,
    val keepScreenOn: Boolean = false,
    val websiteRateUnlocked: Boolean = false,
    val runState: RunState = RunState.IDLE,
    val cooldownRemainingSeconds: Int = 0,
    val validationError: String? = null,
    val stats: StatsSnapshot? = null,
    val history: List<RunSummary> = emptyList(),
    val showSettings: Boolean = false,
    val showHistory: Boolean = false
) {
    val targetLabel: String
        get() = when (targetKind) {
            TargetKind.LOCAL_IP -> "$ipInput:$port"
            TargetKind.WEBSITE -> websiteInput
        }
}
