package com.hammer.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live figures the running ViewModel pushes to the foreground notification (§11). */
data class RunNotificationState(
    val targetLabel: String,
    val currentRps: Long,
    val elapsedSeconds: Int,
    val totalDurationSeconds: Int
)

/**
 * One-way channel from the ViewModel to [HammerForegroundService] so the persistent notification can
 * show live req/s and run progress. The service collects [state] and re-issues the notification; the
 * ViewModel publishes on every stats tick and clears it when the run ends.
 */
object RunNotificationBus {
    private val _state = MutableStateFlow<RunNotificationState?>(null)
    val state = _state.asStateFlow()

    fun publish(newState: RunNotificationState) {
        _state.value = newState
    }

    fun clear() {
        _state.value = null
    }
}
