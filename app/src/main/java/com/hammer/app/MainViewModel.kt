package com.hammer.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hammer.app.core.IpValidator
import com.hammer.app.engine.FinishReason
import com.hammer.app.engine.LoadProfile
import com.hammer.app.engine.Protocol
import com.hammer.app.engine.RunConfig
import com.hammer.app.engine.RunEngine
import com.hammer.app.engine.TargetConfig
import com.hammer.app.export.CsvExporter
import com.hammer.app.export.HammerStorage
import com.hammer.app.export.JsonExporter
import com.hammer.app.service.HammerForegroundService
import com.hammer.app.service.RunNotificationBus
import com.hammer.app.service.RunNotificationState
import com.hammer.app.service.StopRequestBus
import com.hammer.app.stats.StatsCollector
import com.hammer.app.stats.StatsSnapshot
import com.hammer.app.ui.HammerUiState
import com.hammer.app.ui.ProfileChoice
import com.hammer.app.ui.ProtocolChoice
import com.hammer.app.ui.RunState
import com.hammer.app.ui.RunSummary
import com.hammer.app.ui.TargetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val COOLDOWN_SECONDS = 5
        // Addendum 16.1: unverified "site internet" targets stay capped low until explicitly unlocked.
        private const val WEBSITE_DEFAULT_RATE_CAP = 20
        private const val STATS_POLL_INTERVAL_MS = 500L
        private const val RAMP_DURATION_SECONDS = 30
        private const val BURST_REQUESTS = 200
        private const val BURST_WINDOW_MILLIS = 1_000L
        private const val BURST_PAUSE_SECONDS = 3
    }

    private val _uiState = MutableStateFlow(HammerUiState())
    val uiState: StateFlow<HammerUiState> = _uiState.asStateFlow()

    private var runEngine: RunEngine? = null
    private var statsCollector: StatsCollector? = null
    private var statsPollingJob: Job? = null
    private var pendingConfig: RunConfig? = null
    private var lastRunFinishedAtMillis: Long = 0L

    init {
        viewModelScope.launch {
            StopRequestBus.events.collect { stopRun() }
        }
    }

    fun updateTargetKind(kind: TargetKind) = update {
        // Flip the conventional default port when the user hasn't overridden it: 443 for a TLS
        // website, 80 for a plain-HTTP LAN device. A website on :80 with TLS would never connect.
        val port = when {
            kind == TargetKind.WEBSITE && it.port == "80" -> "443"
            kind == TargetKind.LOCAL_IP && it.port == "443" -> "80"
            else -> it.port
        }
        it.copy(targetKind = kind, port = port, validationError = null)
    }
    fun updateIpInput(value: String) = update { it.copy(ipInput = value, validationError = null) }
    fun updateWebsiteInput(value: String) = update { it.copy(websiteInput = value, validationError = null) }
    fun updatePort(value: String) = update { it.copy(port = value.filter(Char::isDigit)) }
    fun updatePath(value: String) = update { it.copy(path = value) }
    fun updateProtocol(protocol: ProtocolChoice) = update { it.copy(protocol = protocol) }
    fun updateProfile(profile: ProfileChoice) = update { it.copy(profile = profile) }
    fun updateConcurrency(value: Int) = update { it.copy(concurrency = value.coerceIn(1, 200)) }
    fun updateDurationSeconds(value: Int) = update { it.copy(durationSeconds = value) }
    fun updateRateLimitEnabled(enabled: Boolean) = update { it.copy(rateLimitEnabled = enabled) }
    fun updateRateLimitRps(value: Int) = update { it.copy(rateLimitRps = value.coerceIn(1, 1000)) }
    fun updateTimeoutMillis(value: Long) = update { it.copy(timeoutMillis = value) }
    fun updateIgnoreTlsErrors(enabled: Boolean) = update { it.copy(ignoreTlsErrors = enabled) }
    fun updateKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOn = enabled) }
    fun unlockWebsiteRate() = update { it.copy(websiteRateUnlocked = true) }
    fun toggleSettings(show: Boolean) = update { it.copy(showSettings = show) }
    fun toggleHistory(show: Boolean) = update { it.copy(showHistory = show) }

    fun applyFriendlyMode() = update {
        it.copy(
            protocol = ProtocolChoice.HTTP_GET,
            profile = ProfileChoice.CONSTANT,
            concurrency = 50,
            durationSeconds = 30,
            rateLimitEnabled = true,
            rateLimitRps = 50
        )
    }

    fun requestStart() {
        val state = _uiState.value
        val cooldownRemaining = cooldownRemainingSeconds()
        if (cooldownRemaining > 0) {
            val message = getApplication<Application>().getString(R.string.error_cooldown, cooldownRemaining)
            update { it.copy(cooldownRemainingSeconds = cooldownRemaining, validationError = message) }
            return
        }

        val config = buildRunConfig(state) ?: return
        pendingConfig = config
        update { it.copy(runState = RunState.AWAITING_CONFIRMATION, validationError = null) }
    }

    fun cancelConfirmation() {
        pendingConfig = null
        update { it.copy(runState = RunState.IDLE) }
    }

    fun confirmAndGo() {
        val config = pendingConfig ?: return
        pendingConfig = null
        startRun(config)
    }

    fun stopRun() {
        runEngine?.stop(FinishReason.USER_STOP)
    }

    override fun onCleared() {
        // Don't let a run keep its scheduler/thread pools/OkHttp client alive after the VM is gone.
        runEngine?.stop(FinishReason.USER_STOP)
        RunNotificationBus.clear()
        super.onCleared()
    }

    fun exportCurrentRun() {
        val collector = statsCollector ?: return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            HammerStorage.openOutputStream(context, "hammer_run_$timestamp.csv", "text/csv")?.use { stream ->
                CsvExporter.write(collector.exportableRecords(), stream)
            }
            HammerStorage.openOutputStream(context, "hammer_run_$timestamp.json", "application/json")?.use { stream ->
                JsonExporter.write(collector.snapshot(), stream)
            }
        }
    }

    private fun cooldownRemainingSeconds(): Int {
        if (lastRunFinishedAtMillis == 0L) return 0
        val elapsedSeconds = (System.currentTimeMillis() - lastRunFinishedAtMillis) / 1000
        return (COOLDOWN_SECONDS - elapsedSeconds).toInt().coerceAtLeast(0)
    }

    private fun buildRunConfig(state: HammerUiState): RunConfig? {
        val target = resolveTarget(state) ?: return null

        val effectiveRate = when {
            state.targetKind == TargetKind.WEBSITE && !state.websiteRateUnlocked ->
                state.rateLimitRps.coerceAtMost(WEBSITE_DEFAULT_RATE_CAP)
            state.rateLimitEnabled -> state.rateLimitRps
            else -> state.rateLimitRps
        }

        val profile = when (state.profile) {
            ProfileChoice.CONSTANT -> LoadProfile.Constant(effectiveRate)
            ProfileChoice.RAMP -> LoadProfile.Ramp(0, effectiveRate, RAMP_DURATION_SECONDS)
            ProfileChoice.BURST -> LoadProfile.Burst(BURST_REQUESTS, BURST_WINDOW_MILLIS, BURST_PAUSE_SECONDS, maxCycles = null)
            ProfileChoice.MAX -> {
                if (state.targetKind == TargetKind.WEBSITE && !state.websiteRateUnlocked) {
                    LoadProfile.Constant(WEBSITE_DEFAULT_RATE_CAP)
                } else {
                    LoadProfile.Max
                }
            }
        }

        val protocol = if (state.targetKind == TargetKind.WEBSITE && state.protocol == ProtocolChoice.TCP_RAW) {
            ProtocolChoice.HTTP_GET // §11: TCP raw disabled in website mode
        } else {
            state.protocol
        }

        return RunConfig(
            target = target,
            protocol = when (protocol) {
                ProtocolChoice.HTTP_GET -> Protocol.HTTP_GET
                ProtocolChoice.HTTP_POST -> Protocol.HTTP_POST
                ProtocolChoice.TCP_RAW -> Protocol.TCP_RAW
            },
            profile = profile,
            concurrency = state.concurrency,
            durationSeconds = state.durationSeconds,
            timeoutMillis = state.timeoutMillis,
            tcpPacketSizeBytes = state.tcpPacketSizeBytes,
            tcpPacketPattern = state.tcpPacketPattern,
            ignoreTlsErrors = state.ignoreTlsErrors && state.targetKind == TargetKind.LOCAL_IP
        )
    }

    private fun resolveTarget(state: HammerUiState): TargetConfig? {
        val port = state.port.toIntOrNull() ?: if (state.targetKind == TargetKind.WEBSITE) 443 else 80
        return when (state.targetKind) {
            TargetKind.LOCAL_IP -> {
                when (IpValidator.validate(state.ipInput)) {
                    IpValidator.Result.Allowed -> TargetConfig.LocalIp(state.ipInput.trim(), port, state.path)
                    IpValidator.Result.RejectedIpv6 -> {
                        showError(R.string.error_ip_ipv6_unsupported)
                        null
                    }
                    else -> {
                        showError(R.string.error_ip_not_rfc1918)
                        null
                    }
                }
            }
            TargetKind.WEBSITE -> TargetConfig.Website(state.websiteInput.trim(), port, state.path, useTls = true)
        }
    }

    private fun showError(resId: Int) {
        val message = getApplication<Application>().getString(resId)
        update { it.copy(validationError = message) }
    }

    private fun startRun(config: RunConfig) {
        val collector = StatsCollector()
        statsCollector = collector

        val engine = RunEngine(
            config = config,
            onOutcome = { outcome -> collector.record(outcome) },
            onFinished = { reason -> onRunFinished(reason) }
        )
        runEngine = engine
        engine.start()

        val context = getApplication<Application>()
        val targetLabel = _uiState.value.targetLabel
        RunNotificationBus.publish(
            RunNotificationState(targetLabel, currentRps = 0, elapsedSeconds = 0, totalDurationSeconds = config.durationSeconds)
        )
        context.startForegroundService(
            Intent(context, HammerForegroundService::class.java)
                .putExtra(HammerForegroundService.EXTRA_TARGET_LABEL, targetLabel)
        )

        update { it.copy(runState = RunState.RUNNING, stats = collector.snapshot()) }

        statsPollingJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = collector.snapshot()
                update { it.copy(stats = snapshot) }
                RunNotificationBus.publish(
                    RunNotificationState(
                        targetLabel = targetLabel,
                        currentRps = snapshot.currentRps,
                        elapsedSeconds = snapshot.elapsedSeconds.toInt(),
                        totalDurationSeconds = config.durationSeconds
                    )
                )
                delay(STATS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun onRunFinished(reason: FinishReason) {
        statsPollingJob?.cancel()
        lastRunFinishedAtMillis = System.currentTimeMillis()

        val context = getApplication<Application>()
        RunNotificationBus.clear()
        context.stopService(Intent(context, HammerForegroundService::class.java))

        val snapshot = statsCollector?.snapshot()
        val targetLabel = _uiState.value.targetLabel
        update {
            it.copy(
                runState = RunState.FINISHED,
                stats = snapshot,
                validationError = if (reason == FinishReason.AUTO_STOP) {
                    getApplication<Application>().getString(R.string.error_auto_stop)
                } else {
                    null
                },
                history = if (snapshot != null) {
                    (listOf(snapshot.toSummary(targetLabel)) + it.history).take(10)
                } else {
                    it.history
                }
            )
        }
    }

    private fun StatsSnapshot.toSummary(targetLabel: String) = RunSummary(
        timestampMillis = System.currentTimeMillis(),
        targetLabel = targetLabel,
        avgRps = if (elapsedSeconds > 0) totalSent.toDouble() / elapsedSeconds else 0.0,
        peakRps = peakRps,
        errorRatePercent = if (totalSent > 0) totalFailed * 100.0 / totalSent else 0.0
    )

    private fun update(transform: (HammerUiState) -> HammerUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
