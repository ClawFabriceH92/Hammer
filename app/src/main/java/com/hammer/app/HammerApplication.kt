package com.hammer.app

import android.app.Application

/**
 * No telemetry, no crash reporting SDK is wired up here or anywhere else in the app
 * (addendum 16.5) — everything the app produces stays in Documents/Hammer/ on the device.
 */
class HammerApplication : Application()
