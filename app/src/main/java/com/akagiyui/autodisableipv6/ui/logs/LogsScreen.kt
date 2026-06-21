package com.akagiyui.autodisableipv6.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akagiyui.autodisableipv6.R
import com.akagiyui.autodisableipv6.data.LogEntry
import com.akagiyui.autodisableipv6.data.LogType
import com.akagiyui.autodisableipv6.ui.formatTimestamp

@Composable
fun LogsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: LogsViewModel = viewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    if (logs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.logs_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(logs, key = { it.id }) { entry ->
            LogCard(entry)
        }
    }
}

@Composable
private fun LogCard(entry: LogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogTypeChip(entry.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(top = 6.dp))
            Text(text = entry.message, style = MaterialTheme.typography.bodyMedium)
            entry.ssid?.let { ssid ->
                Text(
                    text = "SSID: $ssid",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Spacer(Modifier.padding(top = 6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogTypeChip(type: LogType) {
    val color = logTypeColor(type)
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = logTypeLabel(type),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun logTypeColor(type: LogType): Color = when (type) {
    LogType.TRIGGER -> MaterialTheme.colorScheme.primary
    LogType.EXECUTE -> MaterialTheme.colorScheme.tertiary
    LogType.SUCCESS -> Color(0xFF2E9E4F)
    LogType.FAILURE -> MaterialTheme.colorScheme.error
    LogType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun logTypeLabel(type: LogType): String = stringResource(
    when (type) {
        LogType.TRIGGER -> R.string.log_type_trigger
        LogType.EXECUTE -> R.string.log_type_execute
        LogType.SUCCESS -> R.string.log_type_success
        LogType.FAILURE -> R.string.log_type_failure
        LogType.INFO -> R.string.log_type_info
    }
)
