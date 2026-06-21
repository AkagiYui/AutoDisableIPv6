package com.akagiyui.autodisableipv6.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.logsDataStore: DataStore<Preferences> by preferencesDataStore(name = "logs")

/**
 * Persists trigger / execution / result log lines so the history survives reboots.
 * The newest entry is kept first; the list is capped to [MAX_ENTRIES].
 */
class LogRepository(private val context: Context) {

    private val key = stringPreferencesKey("entries_json")

    val logs: Flow<List<LogEntry>> = context.logsDataStore.data
        .map { p -> parse(p[key]) }
        .distinctUntilChanged()

    suspend fun add(
        type: LogType,
        message: String,
        ssid: String? = null,
        detail: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val entry = LogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            type = type,
            ssid = ssid,
            message = message,
            detail = detail,
        )
        context.logsDataStore.edit { p ->
            val list = parse(p[key]).toMutableList()
            list.add(0, entry)
            if (list.size > MAX_ENTRIES) {
                list.subList(MAX_ENTRIES, list.size).clear()
            }
            p[key] = serialize(list)
        }
    }

    suspend fun clear() {
        context.logsDataStore.edit { it[key] = "[]" }
    }

    private fun parse(json: String?): List<LogEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                LogEntry(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    timestamp = o.optLong("timestamp", 0L),
                    type = runCatching { LogType.valueOf(o.optString("type", "INFO")) }
                        .getOrDefault(LogType.INFO),
                    ssid = o.nullableString("ssid"),
                    message = o.optString("message", ""),
                    detail = o.nullableString("detail"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serialize(list: List<LogEntry>): String {
        val array = JSONArray()
        list.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("timestamp", e.timestamp)
                    .put("type", e.type.name)
                    .put("ssid", e.ssid ?: JSONObject.NULL)
                    .put("message", e.message)
                    .put("detail", e.detail ?: JSONObject.NULL)
            )
        }
        return array.toString()
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    companion object {
        const val MAX_ENTRIES = 2000
    }
}
