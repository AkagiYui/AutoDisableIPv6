package com.akagiyui.autodisableipv6.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.akagiyui.autodisableipv6.data.AppSettings

/** Single entry point for starting/stopping the long-running monitor service. */
object ServiceController {

    fun start(context: Context) {
        val intent = Intent(context.applicationContext, MonitorService::class.java)
        // Background starts are restricted on Android 12+; swallow the rare
        // ForegroundServiceStartNotAllowedException rather than crashing.
        runCatching { ContextCompat.startForegroundService(context.applicationContext, intent) }
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, MonitorService::class.java)
        )
    }

    /** Brings the service into line with the master switch. */
    fun sync(context: Context, settings: AppSettings) {
        if (settings.masterEnabled) start(context) else stop(context)
    }
}
