package com.akagiyui.autodisableipv6.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of a root command: combined stdout+stderr plus the process exit code. */
data class CommandResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
)

/**
 * Runs shell snippets as root through `su -c`. This is the only supported
 * escalation method for now; other strategies are intentionally out of scope.
 */
object RootShell {

    /** Runs [script] via `su -c`, capturing stdout and stderr together. */
    suspend fun run(script: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            CommandResult(success = code == 0, exitCode = code, output = output.trim())
        } catch (e: Exception) {
            // `su` missing (un-rooted) or denied -> start()/waitFor() throws.
            CommandResult(success = false, exitCode = -1, output = e.message ?: e.toString())
        }
    }

    /**
     * Probes for working root by running `id` and confirming we are actually uid 0.
     * Triggers the superuser authorization prompt on first use.
     */
    suspend fun testRoot(): CommandResult {
        val result = run("id")
        val isRoot = result.output.contains("uid=0")
        return result.copy(success = result.success && isRoot)
    }
}
