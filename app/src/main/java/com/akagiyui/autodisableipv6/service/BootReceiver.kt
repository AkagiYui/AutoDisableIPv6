package com.akagiyui.autodisableipv6.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.akagiyui.autodisableipv6.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the monitor after a reboot so that a device already connected to a
 * target network has IPv6 disabled without the app being opened. Gated by both
 * the master switch and the "auto-start on boot" preference.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in BOOT_ACTIONS) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = context.app.settingsRepository.currentSettings()
                if (settings.masterEnabled && settings.autoStartOnBoot) {
                    ServiceController.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
