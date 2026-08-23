package com.hammer.app.engine

import com.hammer.app.core.AutoStopWatcher
import com.hammer.app.core.ConcurrencyGuard
import com.hammer.app.core.TokenBucketRateLimiter
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives one run end to end: paces requests according to [RunConfig.profile], enforces the
 * concurrency hard cap and rate limit, watches the failure rate, and dispatches each request to
 * [HttpEngine] or [TcpRawEngine] on a worker pool.
 */
class RunEngine(
    private val config: RunConfig,
    private val onOutcome: (RequestOutcome) -> Unit,
    private val onFinished: (FinishReason) -> Unit
) {
    companion object {
        private const val TICK_INTERVAL_MILLIS = 50L
        private const val MAX_LAUNCHES_PER_TICK = 200
    }

    private val concurrencyGuard = ConcurrencyGuard(config.concurrency, config.debugConcurrencyOverride)
    private val rateLimiter: TokenBucketRateLimiter? = initialRateLimiter()
    private val autoStopWatcher = AutoStopWatcher(onTrip = { stop(FinishReason.AUTO_STOP) })
    private val httpClient = if (config.protocol != Protocol.TCP_RAW) HttpEngine.buildClient(config) else null

    private val workerPool = Executors.newCachedThreadPool()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    // stop() can be reached concurrently from three places — the auto-stop watcher (a worker
    // thread), the duration check (the scheduler thread) and the user's STOP (the main thread).
    // These flags make start()/stop() idempotent so onFinished fires exactly once.
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    @Volatile private var startedAtMillis = 0L

    @Volatile private var burstCycleCount = 0
    @Volatile private var burstLaunchedThisCycle = 0
    @Volatile private var inBurstPause = false
    @Volatile private var phaseStartedAtMillis = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        startedAtMillis = System.currentTimeMillis()
        phaseStartedAtMillis = startedAtMillis
        scheduler.scheduleAtFixedRate({ safeTick() }, 0, TICK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun stop(reason: FinishReason = FinishReason.USER_STOP) {
        if (!stopped.compareAndSet(false, true)) return
        scheduler.shutdown()
        workerPool.shutdown()
        // Release the OkHttp dispatcher's thread pool and idle connections instead of leaking them
        // across runs (a fresh client is built for every run).
        httpClient?.dispatcher?.executorService?.shutdown()
        httpClient?.connectionPool?.evictAll()
        onFinished(reason)
    }

    private fun safeTick() {
        // scheduleAtFixedRate silently cancels all future ticks if the task throws once —
        // an unexpected exception here must not stop the run's own duration/auto-stop timers.
        try {
            tick()
        } catch (_: Throwable) {
        }
    }

    private fun tick() {
        if (stopped.get()) return
        val now = System.currentTimeMillis()
        val elapsedMillis = now - startedAtMillis
        if (config.durationSeconds > 0 && elapsedMillis >= config.durationSeconds * 1000L) {
            stop(FinishReason.DURATION_ELAPSED)
            return
        }

        when (val profile = config.profile) {
            is LoadProfile.Constant -> rateLimiter?.let { drainRateLimiter(it) }
            is LoadProfile.Ramp -> {
                rateLimiter?.updateRate(currentRampRate(profile, elapsedMillis))
                rateLimiter?.let { drainRateLimiter(it) }
            }
            is LoadProfile.Burst -> tickBurst(profile, now)
            LoadProfile.Max -> fillConcurrency()
        }
    }

    private fun currentRampRate(profile: LoadProfile.Ramp, elapsedMillis: Long): Int {
        val progress = (elapsedMillis / 1000.0 / profile.rampDurationSeconds).coerceIn(0.0, 1.0)
        val rate = profile.fromRps + (profile.toRps - profile.fromRps) * progress
        return rate.toInt().coerceAtLeast(1)
    }

    private fun drainRateLimiter(limiter: TokenBucketRateLimiter) {
        var launched = 0
        while (launched < MAX_LAUNCHES_PER_TICK && limiter.tryAcquire()) {
            if (!concurrencyGuard.tryAcquire()) break
            launchOne()
            launched++
        }
    }

    private fun fillConcurrency() {
        var launched = 0
        while (launched < MAX_LAUNCHES_PER_TICK && concurrencyGuard.tryAcquire()) {
            launchOne()
            launched++
        }
    }

    private fun tickBurst(profile: LoadProfile.Burst, now: Long) {
        if (inBurstPause) {
            if (now - phaseStartedAtMillis >= profile.pauseSeconds * 1000L) {
                if (profile.maxCycles != null && burstCycleCount >= profile.maxCycles) {
                    stop(FinishReason.DURATION_ELAPSED)
                    return
                }
                inBurstPause = false
                phaseStartedAtMillis = now
                burstLaunchedThisCycle = 0
            }
            return
        }

        val burstElapsed = now - phaseStartedAtMillis
        if (burstElapsed >= profile.burstWindowMillis || burstLaunchedThisCycle >= profile.requestsPerBurst) {
            burstCycleCount++
            inBurstPause = true
            phaseStartedAtMillis = now
            return
        }

        val remaining = profile.requestsPerBurst - burstLaunchedThisCycle
        val toLaunchThisTick = remaining.coerceAtMost(MAX_LAUNCHES_PER_TICK)
        repeat(toLaunchThisTick) {
            if (concurrencyGuard.tryAcquire()) {
                launchOne()
                burstLaunchedThisCycle++
            }
        }
    }

    private fun launchOne() {
        try {
            workerPool.submit {
                try {
                    val outcome = when (config.protocol) {
                        Protocol.TCP_RAW -> TcpRawEngine.execute(config)
                        else -> HttpEngine.execute(httpClient!!, config)
                    }
                    autoStopWatcher.record(outcome.success)
                    onOutcome(outcome)
                } finally {
                    concurrencyGuard.release()
                }
            }
        } catch (_: RejectedExecutionException) {
            // The pool was shut down between the caller acquiring a permit and this submit
            // (a stop() raced with a tick). Give the permit back so the guard stays balanced.
            concurrencyGuard.release()
        }
    }

    private fun initialRateLimiter(): TokenBucketRateLimiter? = when (val profile = config.profile) {
        is LoadProfile.Constant -> TokenBucketRateLimiter(profile.requestsPerSecond)
        is LoadProfile.Ramp -> TokenBucketRateLimiter(profile.fromRps.coerceAtLeast(1))
        is LoadProfile.Burst -> null
        LoadProfile.Max -> null
    }
}
