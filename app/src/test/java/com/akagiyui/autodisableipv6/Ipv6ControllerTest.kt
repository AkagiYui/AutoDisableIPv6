package com.akagiyui.autodisableipv6

import com.akagiyui.autodisableipv6.core.Ipv6Controller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv6ControllerTest {

    @Test
    fun sanitize_falls_back_to_wlan0() {
        assertEquals("wlan0", Ipv6Controller.sanitizeIface(null))
        assertEquals("wlan0", Ipv6Controller.sanitizeIface(""))
        assertEquals("wlan0", Ipv6Controller.sanitizeIface("bad name; rm -rf"))
        assertEquals("wlan1", Ipv6Controller.sanitizeIface("wlan1"))
        assertEquals("eth0", Ipv6Controller.sanitizeIface("eth0"))
    }

    @Test
    fun script_targets_detected_interface() {
        val script = Ipv6Controller.buildScript("wlan0")
        assertTrue(script.contains("/proc/sys/net/ipv6/conf/wlan0/accept_ra"))
        assertTrue(script.contains("/proc/sys/net/ipv6/conf/all/disable_ipv6"))
        assertTrue(script.contains("echo 0"))
        assertTrue(script.contains("echo 1"))
    }

    @Test
    fun script_sanitizes_injection_attempt() {
        val script = Ipv6Controller.buildScript("wlan0; reboot")
        assertTrue(script.contains("wlan0/accept_ra"))
        // The malicious suffix must not survive into the command.
        assertTrue(!script.contains("reboot"))
    }
}
