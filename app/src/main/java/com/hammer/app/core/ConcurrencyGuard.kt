package com.hammer.app.core

import java.util.concurrent.Semaphore

class ConcurrencyGuard(
    requestedConcurrency: Int,
    debugFlagEnabled: Boolean = false
) {
    companion object {
        const val HARD_CAP = 500
    }

    val effectiveConcurrency: Int = if (debugFlagEnabled) {
        requestedConcurrency.coerceAtLeast(1)
    } else {
        requestedConcurrency.coerceIn(1, HARD_CAP)
    }

    private val semaphore = Semaphore(effectiveConcurrency)

    fun acquire() = semaphore.acquire()
    fun release() = semaphore.release()
    fun tryAcquire(): Boolean = semaphore.tryAcquire()
    fun availablePermits(): Int = semaphore.availablePermits()
}
