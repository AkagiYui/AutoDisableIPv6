package com.akagiyui.autodisableipv6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akagiyui.autodisableipv6.service.ServiceController
import com.akagiyui.autodisableipv6.ui.logs.LogsScreen
import com.akagiyui.autodisableipv6.ui.logs.LogsViewModel
import com.akagiyui.autodisableipv6.ui.settings.SettingsScreen
import com.akagiyui.autodisableipv6.ui.settings.SettingsViewModel
import com.akagiyui.autodisableipv6.ui.theme.AutoDisableIPv6Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Re-attach the monitor when the app is opened, if the user opted in.
        lifecycleScope.launch {
            val settings = app.settingsRepository.currentSettings()
            if (settings.masterEnabled && settings.autoResumeOnLaunch) {
                ServiceController.start(this@MainActivity)
            }
        }

        setContent {
            AutoDisableIPv6Theme {
                AppRoot()
            }
        }
    }
}

private enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    Settings(R.string.nav_settings, Icons.Filled.Settings),
    Logs(R.string.nav_logs, Icons.AutoMirrored.Filled.List),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val logsViewModel: LogsViewModel = viewModel()

    var tab by rememberSaveable { mutableStateOf(AppTab.Settings) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.titleRes)) },
                actions = {
                    if (tab == AppTab.Logs) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.logs_clear),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.titleRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (tab) {
            AppTab.Settings -> SettingsScreen(
                contentPadding = innerPadding,
                viewModel = settingsViewModel,
            )

            AppTab.Logs -> LogsScreen(
                contentPadding = innerPadding,
                viewModel = logsViewModel,
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.logs_clear_confirm_title)) },
            text = { Text(stringResource(R.string.logs_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    logsViewModel.clear()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.logs_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
