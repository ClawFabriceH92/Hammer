package com.hammer.app.engine

sealed class TargetConfig {
    data class LocalIp(val ip: String, val port: Int, val path: String = "/", val useTls: Boolean = false) : TargetConfig()
    data class Website(val host: String, val port: Int, val path: String, val useTls: Boolean) : TargetConfig()
}

enum class Protocol { HTTP_GET, HTTP_POST, TCP_RAW }

enum class PacketPattern { ZERO, RANDOM, TEXT }

sealed class LoadProfile {
    data class Constant(val requestsPerSecond: Int) : LoadProfile()
    data class Ramp(val fromRps: Int, val toRps: Int, val rampDurationSeconds: Int) : LoadProfile()
    data class Burst(val requestsPerBurst: Int, val burstWindowMillis: Long, val pauseSeconds: Int, val maxCycles: Int?) : LoadProfile()
    data object Max : LoadProfile()
}

data class RunConfig(
    val target: TargetConfig,
    val protocol: Protocol,
    val profile: LoadProfile,
    val concurrency: Int,
    val durationSeconds: Int,
    val timeoutMillis: Long,
    val customHeaders: Map<String, String> = emptyMap(),
    val postBody: ByteArray? = null,
    val postContentType: String = "application/octet-stream",
    val tcpPacketSizeBytes: Int = 64,
    val tcpPacketPattern: PacketPattern = PacketPattern.ZERO,
    val ignoreTlsErrors: Boolean = false,
    val debugConcurrencyOverride: Boolean = false
)

enum class ErrorCategory { CONNECTION_REFUSED, TIMEOUT, RESET, SSL_ERROR, HTTP_4XX, HTTP_5XX, OTHER, NONE }

data class RequestOutcome(
    val success: Boolean,
    val latencyMillis: Long,
    val errorCategory: ErrorCategory,
    val httpStatusCode: Int? = null
)

enum class FinishReason { USER_STOP, DURATION_ELAPSED, AUTO_STOP, COOLDOWN_REJECTED }
