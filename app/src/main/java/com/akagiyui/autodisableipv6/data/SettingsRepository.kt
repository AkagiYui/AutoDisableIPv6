package com.akagiyui.autodisableipv6.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists the global switches and the SSID rule list. Rules are stored as a JSON
 * string inside the same preferences file (the list is small, so this is plenty fast
 * and avoids pulling in a database / annotation processor).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val MASTER = booleanPreferencesKey("master_enabled")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume_on_launch")
        val AUTO_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val ONLY_UNLOCKED = booleanPreferencesKey("only_when_unlocked")
        val RETRY = booleanPreferencesKey("retry_on_failure")
        val RULES = stringPreferencesKey("ssid_rules_json")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .map { p ->
            AppSettings(
                masterEnabled = p[Keys.MASTER] ?: false,
                autoResumeOnLaunch = p[Keys.AUTO_RESUME] ?: true,
                autoStartOnBoot = p[Keys.AUTO_BOOT] ?: false,
                onlyWhenUnlocked = p[Keys.ONLY_UNLOCKED] ?: false,
                retryOnFailure = p[Keys.RETRY] ?: true,
            )
        }
        .distinctUntilChanged()

    val rules: Flow<List<SsidRule>> = context.settingsDataStore.data
        .map { p -> parseRules(p[Keys.RULES]) }
        .distinctUntilChanged()

    /** One-shot snapshot, used by the service where collecting a flow is awkward. */
    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun currentRules(): List<SsidRule> = rules.first()

    suspend fun setMasterEnabled(value: Boolean) = put(Keys.MASTER, value)
    suspend fun setAutoResumeOnLaunch(value: Boolean) = put(Keys.AUTO_RESUME, value)
    suspend fun setAutoStartOnBoot(value: Boolean) = put(Keys.AUTO_BOOT, value)
    suspend fun setOnlyWhenUnlocked(value: Boolean) = put(Keys.ONLY_UNLOCKED, value)
    suspend fun setRetryOnFailure(value: Boolean) = put(Keys.RETRY, value)

    suspend fun upsertRule(rule: SsidRule) {
        context.settingsDataStore.edit { p ->
            val list = parseRules(p[Keys.RULES]).toMutableList()
            val index = list.indexOfFirst { it.id == rule.id }
            if (index >= 0) list[index] = rule else list.add(rule)
            p[Keys.RULES] = serializeRules(list)
        }
    }

    suspend fun deleteRule(id: String) {
        context.settingsDataStore.edit { p ->
            val list = parseRules(p[Keys.RULES]).filterNot { it.id == id }
            p[Keys.RULES] = serializeRules(list)
        }
    }

    suspend fun setRuleEnabled(id: String, enabled: Boolean) {
        context.settingsDataStore.edit { p ->
            val list = parseRules(p[Keys.RULES]).map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            p[Keys.RULES] = serializeRules(list)
        }
    }

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private fun parseRules(json: String?): List<SsidRule> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SsidRule(
                    id = o.getString("id"),
                    pattern = o.getString("pattern"),
                    label = o.optString("label", ""),
                    enabled = o.optBoolean("enabled", true),
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        }.getOrDefault(emptyList()).sortedBy { it.createdAt }
    }

    private fun serializeRules(rules: List<SsidRule>): String {
        val array = JSONArray()
        rules.sortedBy { it.createdAt }.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("pattern", rule.pattern)
                    .put("label", rule.label)
                    .put("enabled", rule.enabled)
                    .put("createdAt", rule.createdAt)
            )
        }
        return array.toString()
    }
}
