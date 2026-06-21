package com.akagiyui.autodisableipv6

import com.akagiyui.autodisableipv6.core.SsidMatcher
import com.akagiyui.autodisableipv6.data.SsidRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsidMatcherTest {

    private fun rule(pattern: String, enabled: Boolean = true) =
        SsidRule(id = pattern, pattern = pattern, label = "", enabled = enabled, createdAt = 0)

    @Test
    fun normalize_strips_surrounding_quotes() {
        assertEquals("MyWifi", SsidMatcher.normalizeSsid("\"MyWifi\""))
        assertEquals("MyWifi", SsidMatcher.normalizeSsid("MyWifi"))
        assertNull(SsidMatcher.normalizeSsid(null))
    }

    @Test
    fun unknown_detection() {
        assertTrue(SsidMatcher.isUnknown(null))
        assertTrue(SsidMatcher.isUnknown(""))
        assertTrue(SsidMatcher.isUnknown("<unknown ssid>"))
        assertFalse(SsidMatcher.isUnknown("Home"))
    }

    @Test
    fun full_match_semantics() {
        assertTrue(SsidMatcher.matches("Home.*", "HomeWifi"))
        assertTrue(SsidMatcher.matches("Home-\\d+", "Home-5"))
        // Partial matches must fail because matching is anchored to the whole SSID.
        assertFalse(SsidMatcher.matches("Home", "HomeWifi"))
        assertFalse(SsidMatcher.matches("ifi", "HomeWifi"))
    }

    @Test
    fun invalid_pattern_never_matches() {
        assertFalse(SsidMatcher.matches("[", "anything"))
    }

    @Test
    fun firstMatch_skips_disabled_rules() {
        val rules = listOf(rule("Home.*", enabled = false), rule(".*Wifi"))
        assertEquals(".*Wifi", SsidMatcher.firstMatch(rules, "HomeWifi")?.pattern)
        assertNull(SsidMatcher.firstMatch(listOf(rule("Office.*")), "HomeWifi"))
    }

    @Test
    fun anyMatch_reflects_enabled_state() {
        assertTrue(SsidMatcher.anyMatch(listOf(rule("Home.*")), "HomeNet"))
        assertFalse(SsidMatcher.anyMatch(listOf(rule("Home.*", enabled = false)), "HomeNet"))
    }
}
