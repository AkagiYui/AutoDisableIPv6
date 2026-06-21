package com.akagiyui.autodisableipv6.data

/**
 * A user-defined target. The [pattern] is treated as a full-match regular expression
 * against the connected Wi-Fi SSID. A rule can be disabled without being deleted.
 */
data class SsidRule(
    val id: String,
    val pattern: String,
    val label: String,
    val enabled: Boolean,
    val createdAt: Long,
)

/** Global behaviour switches, persisted in DataStore. */
data class AppSettings(
    val masterEnabled: Boolean = false,
    val autoResumeOnLaunch: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val onlyWhenUnlocked: Boolean = false,
    val retryOnFailure: Boolean = true,
)

/** Category of a log line, rendered with a distinct colour/label in the UI. */
enum class LogType {
    /** A matching network connection was detected. */
    TRIGGER,

    /** The root command is about to run. */
    EXECUTE,

    /** The command finished successfully (exit code 0). */
    SUCCESS,

    /** The command failed (non-zero exit, no root, or an exception). */
    FAILURE,

    /** Anything else worth recording (deferred until unlock, retry cancelled, ...). */
    INFO,
}

/** A single persisted log line. [detail] usually holds the raw command output. */
data class LogEntry(
    val id: String,
    val timestamp: Long,
    val type: LogType,
    val ssid: String?,
    val message: String,
    val detail: String?,
)
