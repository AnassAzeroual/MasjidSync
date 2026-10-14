package com.mosquetracker.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.model.DetectionMethod
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.util.TimeFormat
import com.mosquetracker.app.util.displayName

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val grouped = history.groupBy { TimeFormat.date(it.log.entryTime) }

    ScreenScaffold(title = "Attendance history", modifier = modifier) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.DateRange,
                        title = "No visits recorded",
                        message = "Once a mosque is saved and monitoring is on, your entry and exit times appear here.",
                    )
                }
            }

            grouped.forEach { (date, entries) ->
                item(key = "header-$date") {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(entries, key = { it.log.id }) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: AttendanceWithMosque) {
    val method = runCatching { DetectionMethod.valueOf(entry.log.detectionMethod) }
        .getOrDefault(DetectionMethod.GEOFENCE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.mosqueName ?: "Deleted mosque",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = TimeFormat.duration(entry.log.entryTime, entry.log.exitTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "In ${TimeFormat.time(entry.log.entryTime)}" +
                    (entry.log.exitTime?.let { "  ·  Out ${TimeFormat.time(it)}" } ?: "  ·  still inside"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Detected via ${method.displayName()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
