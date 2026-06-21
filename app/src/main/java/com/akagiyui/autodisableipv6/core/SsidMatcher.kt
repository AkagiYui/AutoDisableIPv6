package com.akagiyui.autodisableipv6.core

import com.akagiyui.autodisableipv6.data.SsidRule
import java.util.regex.Pattern

/**
 * SSID handling helpers. Matching uses full-string regular expressions
 * (`Matcher.matches()`), so a pattern must describe the entire SSID.
 */
object SsidMatcher {

    private const val UNKNOWN_SSID = "<unknown ssid>"

    /** Android wraps SSIDs in double quotes; strip them when present. */
    fun normalizeSsid(raw: String?): String? {
        if (raw == null) return null
        return if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
    }

    /** True when the SSID could not be read (e.g. missing location permission). */
    fun isUnknown(ssid: String?): Boolean =
        ssid.isNullOrEmpty() || ssid == UNKNOWN_SSID || ssid == "0x"

    /** Full-match regex test. An invalid pattern never matches (and never throws). */
    fun matches(pattern: String, ssid: String): Boolean =
        runCatching { Pattern.compile(pattern).matcher(ssid).matches() }.getOrDefault(false)

    /** First enabled rule whose pattern fully matches [ssid], or null. */
    fun firstMatch(rules: List<SsidRule>, ssid: String): SsidRule? =
        rules.firstOrNull { it.enabled && matches(it.pattern, ssid) }

    /** Whether any enabled rule matches [ssid]; used to validate pending retries. */
    fun anyMatch(rules: List<SsidRule>, ssid: String): Boolean =
        firstMatch(rules, ssid) != null
}
