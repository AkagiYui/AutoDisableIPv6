package com.akagiyui.autodisableipv6.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akagiyui.autodisableipv6.App
import com.akagiyui.autodisableipv6.R
import com.akagiyui.autodisableipv6.data.AppSettings
import com.akagiyui.autodisableipv6.data.LogType
import com.akagiyui.autodisableipv6.data.SsidRule
import com.akagiyui.autodisableipv6.root.RootShell
import com.akagiyui.autodisableipv6.service.ServiceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val rules: List<SsidRule> = emptyList(),
)

sealed interface RootTestState {
    data object Idle : RootTestState
    data object Running : RootTestState
    data class Done(val success: Boolean, val output: String) : RootTestState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val settingsRepo = app.settingsRepository
    private val logRepo = app.logRepository

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepo.settings, settingsRepo.rules) { settings, rules ->
            SettingsUiState(settings, rules)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(),
        )

    private val _rootTest = MutableStateFlow<RootTestState>(RootTestState.Idle)
    val rootTest: StateFlow<RootTestState> = _rootTest.asStateFlow()

    fun setMasterEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setMasterEnabled(enabled)
        ServiceController.sync(app, settingsRepo.currentSettings())
    }

    fun setAutoResumeOnLaunch(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoResumeOnLaunch(enabled)
    }

    fun setAutoStartOnBoot(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoStartOnBoot(enabled)
    }

    fun setOnlyWhenUnlocked(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setOnlyWhenUnlocked(enabled)
    }

    fun setRetryOnFailure(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setRetryOnFailure(enabled)
    }

    fun saveRule(id: String?, pattern: String, label: String, enabled: Boolean) =
        viewModelScope.launch {
            val rule = if (id == null) {
                SsidRule(
                    id = UUID.randomUUID().toString(),
                    pattern = pattern,
                    label = label,
                    enabled = enabled,
                    createdAt = System.currentTimeMillis(),
                )
            } else {
                val existing = settingsRepo.currentRules().firstOrNull { it.id == id }
                    ?: return@launch
                existing.copy(pattern = pattern, label = label, enabled = enabled)
            }
            settingsRepo.upsertRule(rule)
        }

    fun deleteRule(id: String) = viewModelScope.launch {
        settingsRepo.deleteRule(id)
    }

    fun setRuleEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setRuleEnabled(id, enabled)
    }

    fun testRoot() = viewModelScope.launch {
        _rootTest.value = RootTestState.Running
        val result = RootShell.testRoot()
        _rootTest.value = RootTestState.Done(result.success, result.output)
        logRepo.add(
            type = if (result.success) LogType.SUCCESS else LogType.FAILURE,
            message = app.getString(
                if (result.success) R.string.log_root_test_ok else R.string.log_root_test_fail
            ),
            detail = result.output.ifBlank { null },
        )
    }

    fun dismissRootTest() {
        _rootTest.value = RootTestState.Idle
    }
}
