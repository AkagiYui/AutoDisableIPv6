package com.akagiyui.autodisableipv6.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akagiyui.autodisableipv6.App
import com.akagiyui.autodisableipv6.data.LogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val logRepo = (application as App).logRepository

    val logs: StateFlow<List<LogEntry>> = logRepo.logs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun clear() = viewModelScope.launch {
        logRepo.clear()
    }
}
