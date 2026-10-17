package com.mosquetracker.app.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.model.DetectionMethod
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.KeyValueRow
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import com.mosquetracker.app.util.Csv
import com.mosquetracker.app.util.TimeFormat
import com.mosquetracker.app.util.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val grouped = history.groupBy { TimeFormat.date(it.log.entryTime) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // System file picker — no storage permission needed, the user chooses where it goes.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = buildCsv(history)
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(csv.toByteArray())
                        }
                    }
                }
            }
        }
    }

    ScreenScaffold(
        title = "History",
        modifier = modifier,
        actions = {
            if (history.isNotEmpty()) {
                IconButton(onClick = { exportLauncher.launch("masjidsync-attendance.csv") }) {
                    Icon(AppIcons.Download, contentDescription = "Export as CSV")
                }
            }
        },
    ) { contentPadding ->
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
            } else {
                item(key = "stats") { StatsCard(stats) }
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
private fun StatsCard(stats: VisitStats) {
    SectionCard(title = "Overview") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(label = "Total", value = stats.total.toString(), modifier = Modifier.weight(1f))
            StatBox(
                label = "This week",
                value = stats.thisWeek.toString(),
                modifier = Modifier.weight(1f),
            )
            StatBox(
                label = "Day streak",
                value = stats.dayStreak.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        stats.perMosque.forEach { (name, count) ->
            KeyValueRow(label = name, value = "$count visit${if (count == 1) "" else "s"}")
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildCsv(history: List<AttendanceWithMosque>): String = buildString {
    appendLine("mosque,entry,exit,duration_minutes,method")
    history.forEach { entry ->
        val duration = entry.log.exitTime?.let { (it - entry.log.entryTime) / 60_000L } ?: ""
        appendLine(
            Csv.row(
                entry.mosqueName ?: "Deleted mosque",
                TimeFormat.dateTime(entry.log.entryTime),
                entry.log.exitTime?.let { TimeFormat.dateTime(it) } ?: "in progress",
                duration.toString(),
                entry.log.detectionMethod,
            ),
        )
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
