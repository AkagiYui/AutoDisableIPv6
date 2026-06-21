package com.akagiyui.autodisableipv6.core

import com.akagiyui.autodisableipv6.root.CommandResult
import com.akagiyui.autodisableipv6.root.RootShell

/**
 * Builds and runs the root commands that disable IPv6 on the active Wi-Fi
 * interface. The interface name is detected at runtime and injected here.
 */
object Ipv6Controller {

    private const val FALLBACK_IFACE = "wlan0"
    private val SAFE_IFACE = Regex("[A-Za-z0-9._-]+")

    /** Falls back to wlan0 if the detected name is missing or looks unsafe. */
    fun sanitizeIface(iface: String?): String =
        iface?.takeIf { it.matches(SAFE_IFACE) } ?: FALLBACK_IFACE

    /** The exact shell snippet that will be run as root. Shown in logs verbatim. */
    fun buildScript(iface: String?): String {
        val name = sanitizeIface(iface)
        return "echo 0 | tee /proc/sys/net/ipv6/conf/$name/accept_ra\n" +
            "echo 1 | tee /proc/sys/net/ipv6/conf/all/disable_ipv6"
    }

    suspend fun disableIpv6(iface: String?): CommandResult = RootShell.run(buildScript(iface))
}
