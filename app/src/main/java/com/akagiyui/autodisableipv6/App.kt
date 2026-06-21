package com.akagiyui.autodisableipv6

import android.app.Application
import android.content.Context
import com.akagiyui.autodisableipv6.data.LogRepository
import com.akagiyui.autodisableipv6.data.SettingsRepository

/**
 * Holds the process-wide singletons. Kept deliberately tiny: a couple of
 * repositories backed by DataStore, reachable from activities, view models,
 * services and receivers via [app].
 */
class App : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var logRepository: LogRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
        logRepository = LogRepository(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}

/** Convenience accessor for the application container from any [Context]. */
val Context.app: App get() = applicationContext as App
