package com.hammer.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Lets the notification's STOP action (fired from the Service, §11/§6) reach the running ViewModel. */
object StopRequestBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun requestStop() {
        _events.tryEmit(Unit)
    }
}
