package com.akagiyui.autodisableipv6.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akagiyui.autodisableipv6.R
import com.akagiyui.autodisableipv6.data.SsidRule
import com.akagiyui.autodisableipv6.ui.batteryOptimizationIntent
import com.akagiyui.autodisableipv6.ui.foregroundLocationPermissions
import com.akagiyui.autodisableipv6.ui.hasBackgroundLocation
import com.akagiyui.autodisableipv6.ui.hasFineLocation
import com.akagiyui.autodisableipv6.ui.hasNotificationPermission
import com.akagiyui.autodisableipv6.ui.isIgnoringBatteryOptimizations
import java.util.regex.Pattern

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rootTest by viewModel.rootTest.collectAsStateWithLifecycle()

    // Re-read permission / battery status whenever we come back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasLocation = remember(refreshKey) { hasFineLocation(context) }
    val hasBgLocation = remember(refreshKey) { hasBackgroundLocation(context) }
    val hasNotif = remember(refreshKey) { hasNotificationPermission(context) }
    val batteryExempt = remember(refreshKey) { isIgnoringBatteryOptimizations(context) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshKey++ }

    var editingRule by remember { mutableStateOf<SsidRule?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingRule by remember { mutableStateOf<SsidRule?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MasterCard(
                enabled = state.settings.masterEnabled,
                onToggle = viewModel::setMasterEnabled,
            )
        }

        // Permissions
        item { SectionHeader(stringResource(R.string.section_permissions)) }
        item {
            SettingsCard {
                PermissionRow(
                    title = stringResource(R.string.perm_location_title),
                    desc = stringResource(R.string.perm_location_desc),
                    granted = hasLocation,
                    onGrant = { locationLauncher.launch(foregroundLocationPermissions) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.perm_background_location_title),
                        desc = stringResource(R.string.perm_background_location_desc),
                        granted = hasBgLocation,
                        onGrant = {
                            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        },
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.perm_notification_title),
                        desc = stringResource(R.string.perm_notification_desc),
                        granted = hasNotif,
                        onGrant = {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }
        }

        // Behavior
        item { SectionHeader(stringResource(R.string.section_behavior)) }
        item {
            SettingsCard {
                SettingSwitchRow(
                    title = stringResource(R.string.behavior_auto_resume_title),
                    desc = stringResource(R.string.behavior_auto_resume_desc),
                    checked = state.settings.autoResumeOnLaunch,
                    onCheckedChange = viewModel::setAutoResumeOnLaunch,
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.behavior_auto_boot_title),
                    desc = stringResource(R.string.behavior_auto_boot_desc),
                    checked = state.settings.autoStartOnBoot,
                    onCheckedChange = viewModel::setAutoStartOnBoot,
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.behavior_only_unlocked_title),
                    desc = stringResource(R.string.behavior_only_unlocked_desc),
                    checked = state.settings.onlyWhenUnlocked,
                    onCheckedChange = viewModel::setOnlyWhenUnlocked,
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = stringResource(R.string.behavior_retry_title),
                    desc = stringResource(R.string.behavior_retry_desc),
                    checked = state.settings.retryOnFailure,
                    onCheckedChange = viewModel::setRetryOnFailure,
                )
            }
        }

        // SSID rules
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionHeader(stringResource(R.string.section_ssid))
                }
                FilledTonalButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ssid_add))
                }
            }
        }
        if (state.rules.isEmpty()) {
            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.ssid_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(state.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { viewModel.setRuleEnabled(rule.id, it) },
                    onEdit = { editingRule = rule },
                    onDelete = { deletingRule = rule },
                )
            }
        }

        // Tools
        item { SectionHeader(stringResource(R.string.section_tools)) }
        item {
            SettingsCard {
                ActionRow(
                    title = stringResource(R.string.tool_test_root),
                    desc = stringResource(R.string.tool_test_root_desc),
                    onClick = viewModel::testRoot,
                )
                HorizontalDivider()
                ActionRow(
                    title = stringResource(R.string.tool_battery),
                    desc = stringResource(
                        if (batteryExempt) R.string.tool_battery_desc_on
                        else R.string.tool_battery_desc_off
                    ),
                    enabled = !batteryExempt,
                    onClick = { batteryLauncher.launch(batteryOptimizationIntent(context)) },
                )
            }
        }
    }

    if (showAddDialog || editingRule != null) {
        val current = editingRule
        RuleDialog(
            rule = current,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { pattern, label, enabled ->
                viewModel.saveRule(current?.id, pattern, label, enabled)
                showAddDialog = false
                editingRule = null
            },
        )
    }

    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(rule.label.ifBlank { rule.pattern }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule.id)
                    deletingRule = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (rootTest != RootTestState.Idle) {
        RootTestDialog(state = rootTest, onDismiss = viewModel::dismissRootTest)
    }
}

@Composable
private fun MasterCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.master_switch_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.master_switch_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) stringResource(R.string.perm_granted) else desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (!granted) {
            FilledTonalButton(onClick = onGrant) {
                Text(stringResource(R.string.perm_grant))
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onClick, enabled = enabled) {
            Text(stringResource(R.string.action_ok))
        }
    }
}

@Composable
private fun RuleCard(
    rule: SsidRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (rule.label.isNotBlank()) {
                    Text(
                        text = rule.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        }
    }
}

@Composable
private fun RuleDialog(
    rule: SsidRule?,
    onDismiss: () -> Unit,
    onSave: (pattern: String, label: String, enabled: Boolean) -> Unit,
) {
    var pattern by remember { mutableStateOf(rule?.pattern ?: "") }
    var label by remember { mutableStateOf(rule?.label ?: "") }
    var enabled by remember { mutableStateOf(rule?.enabled ?: true) }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (rule == null) R.string.ssid_add_title else R.string.ssid_edit_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.ssid_rule_pattern_hint)) },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.ssid_rule_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ssid_rule_enabled),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = pattern.trim()
                when {
                    trimmed.isEmpty() -> error = R.string.ssid_pattern_required
                    !isValidRegex(trimmed) -> error = R.string.ssid_pattern_invalid
                    else -> onSave(trimmed, label.trim(), enabled)
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RootTestDialog(state: RootTestState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (state !is RootTestState.Running) onDismiss() },
        title = { Text(stringResource(R.string.root_test_title)) },
        text = {
            when (state) {
                is RootTestState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.root_test_running))
                    }
                }

                is RootTestState.Done -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(
                                        if (state.success) R.string.root_test_success
                                        else R.string.root_test_failure
                                    )
                                )
                            },
                        )
                        if (state.output.isNotBlank()) {
                            Text(
                                text = state.output,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                RootTestState.Idle -> {}
            }
        },
        confirmButton = {
            if (state !is RootTestState.Running) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
}

private fun isValidRegex(pattern: String): Boolean =
    runCatching { Pattern.compile(pattern) }.isSuccess
