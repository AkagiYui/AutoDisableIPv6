package com.akagiyui.autodisableipv6.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.akagiyui.autodisableipv6.R
import com.akagiyui.autodisableipv6.app
import com.akagiyui.autodisableipv6.core.Ipv6Controller
import com.akagiyui.autodisableipv6.core.SsidMatcher
import com.akagiyui.autodisableipv6.data.LogType
import com.akagiyui.autodisableipv6.data.SsidRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A connected Wi-Fi network as seen from a network callback. */
private data class WifiState(val network: Network, val ssid: String?, val iface: String?)

/**
 * Long-running foreground service. It listens for Wi-Fi changes via a
 * [ConnectivityManager.NetworkCallback] and, whenever the device connects to a
 * network whose SSID matches an enabled rule, runs the IPv6-disable commands as
 * root. Honours the master switch, the unlock requirement, and the retry policy.
 */
class MonitorService : LifecycleService() {

    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    private val keyguardManager by lazy {
        getSystemService(KeyguardManager::class.java)
    }

    private val settingsRepo get() = app.settingsRepository
    private val logRepo get() = app.logRepository

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var unlockReceiver: BroadcastReceiver? = null

    /** The currently connected Wi-Fi network, kept fresh by the callback. */
    @Volatile
    private var current: WifiState? = null

    /** Dedup key so repeated capability callbacks don't re-trigger execution. */
    private var lastKey: String? = null

    /** The in-flight execution (trigger + retries), cancelled when state changes. */
    private var executionJob: Job? = null

    /** A run deferred because the screen was locked. */
    @Volatile
    private var pendingUnlock: WifiState? = null

    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        registerUnlockReceiver()
        registerNetworkCallback()
        lifecycleScope.launch {
            logRepo.add(LogType.INFO, getString(R.string.log_monitor_started))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        executionJob?.cancel()
        unregisterNetworkCallback()
        unregisterUnlockReceiver()
        super.onDestroy()
    }

    private fun startForegroundIfNeeded() {
        if (isForeground) return
        val notification = Notifications.build(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, Notifications.NOTIFICATION_ID, notification, type)
        }.isSuccess
        if (started) {
            isForeground = true
        } else {
            stopSelf()
        }
    }

    // ---- Network monitoring -------------------------------------------------

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    handleCapabilities(network, caps)

                override fun onLost(network: Network) = handleLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    handleCapabilities(network, caps)

                override fun onLost(network: Network) = handleLost(network)
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager ?: return
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun handleCapabilities(network: Network, caps: NetworkCapabilities) {
        val ssid = extractSsid(caps)
        val iface = linkInterface(network)
        val key = "$network|$ssid"
        if (key == lastKey) return // ignore the stream of identical capability updates
        lastKey = key
        val state = WifiState(network, ssid, iface)
        current = state
        onWifiConnected(state)
    }

    private fun handleLost(network: Network) {
        if (current?.network == network) {
            current = null
            lastKey = null
            pendingUnlock = null
            executionJob?.cancel()
        }
    }

    private fun extractSsid(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = caps.transportInfo
            if (info is WifiInfo) return SsidMatcher.normalizeSsid(info.ssid)
        }
        @Suppress("DEPRECATION")
        val legacy = runCatching {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.connectionInfo?.ssid
        }.getOrNull()
        return SsidMatcher.normalizeSsid(legacy)
    }

    private fun linkInterface(network: Network): String? {
        val cm = connectivityManager ?: return null
        val link: LinkProperties? = runCatching { cm.getLinkProperties(network) }.getOrNull()
        return link?.interfaceName
    }

    // ---- Execution ----------------------------------------------------------

    private fun onWifiConnected(state: WifiState) {
        executionJob?.cancel()
        pendingUnlock = null
        executionJob = lifecycleScope.launch {
            val settings = settingsRepo.currentSettings()
            if (!settings.masterEnabled) return@launch
            val ssid = state.ssid
            if (ssid == null || SsidMatcher.isUnknown(ssid)) return@launch // can't match
            val rule = SsidMatcher.firstMatch(settingsRepo.currentRules(), ssid) ?: return@launch

            logRepo.add(LogType.TRIGGER, getString(R.string.log_triggered, ruleName(rule)), ssid)

            if (settings.onlyWhenUnlocked && isLocked()) {
                pendingUnlock = state
                logRepo.add(LogType.INFO, getString(R.string.log_deferred_unlock), ssid)
                return@launch
            }
            runWithRetry(state)
        }
    }

    private fun onUnlocked() {
        val state = pendingUnlock ?: return
        pendingUnlock = null
        executionJob?.cancel()
        executionJob = lifecycleScope.launch {
            if (!isStillValid(state)) {
                logRepo.add(LogType.INFO, getString(R.string.log_deferred_cancelled), state.ssid)
                return@launch
            }
            logRepo.add(LogType.INFO, getString(R.string.log_unlocked_run), state.ssid)
            runWithRetry(state)
        }
    }

    private suspend fun runWithRetry(state: WifiState) {
        val retryEnabled = settingsRepo.currentSettings().retryOnFailure
        val script = Ipv6Controller.buildScript(state.iface)
        var attempt = 0
        while (true) {
            if (!isStillValid(state)) {
                logRepo.add(LogType.INFO, getString(R.string.log_retry_cancelled), state.ssid)
                return
            }
            logRepo.add(LogType.EXECUTE, getString(R.string.log_executing), state.ssid, script)
            val result = Ipv6Controller.disableIpv6(state.iface)
            if (result.success) {
                logRepo.add(
                    LogType.SUCCESS,
                    getString(R.string.log_success),
                    state.ssid,
                    result.output.ifBlank { null },
                )
                return
            }
            logRepo.add(
                LogType.FAILURE,
                getString(R.string.log_failure, result.exitCode),
                state.ssid,
                result.output.ifBlank { null },
            )
            if (!retryEnabled || attempt >= MAX_RETRIES) return
            attempt++
            logRepo.add(
                LogType.INFO,
                getString(R.string.log_retry_scheduled, attempt, MAX_RETRIES),
                state.ssid,
            )
            delay(RETRY_DELAY_MS)
        }
    }

    /** Valid while we are still on the same network and its SSID still matches a rule. */
    private suspend fun isStillValid(state: WifiState): Boolean {
        val cur = current ?: return false
        if (cur.network != state.network) return false
        val ssid = cur.ssid ?: return false
        return SsidMatcher.anyMatch(settingsRepo.currentRules(), ssid)
    }

    private fun isLocked(): Boolean = keyguardManager?.isKeyguardLocked == true

    private fun ruleName(rule: SsidRule): String =
        rule.label.ifBlank { rule.pattern }

    // ---- Unlock receiver ----------------------------------------------------

    private fun registerUnlockReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) onUnlocked()
            }
        }
        unlockReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5_000L
    }
}
