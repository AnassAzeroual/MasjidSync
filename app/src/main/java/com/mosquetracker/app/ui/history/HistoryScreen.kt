package com.mosquetracker.app.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.data.local.AttendanceWithMosque
import com.mosquetracker.app.model.DetectionMethod
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.util.Csv
import com.mosquetracker.app.util.TimeFormat
import com.mosquetracker.app.util.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.WEEK) }
    var selectedMosqueId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val history = state?.history.orEmpty()
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Unable to open export")
                        output.use { it.write(buildCsv(history).toByteArray(Charsets.UTF_8)) }
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "All visits exported" else "Export failed. Please try another location.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    ScreenScaffold(
        title = "History",
        modifier = modifier,
        headerContainerColor = MaterialTheme.colorScheme.surface,
        headerContentColor = MaterialTheme.colorScheme.onSurface,
        actions = {
            TextButton(
                enabled = state?.history?.isNotEmpty() == true,
                onClick = { exportLauncher.launch("masjidsync-attendance.csv") },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.semantics { contentDescription = "Export all visits as CSV" },
            ) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            val loaded = state
            if (loaded == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                HistoryContent(
                    state = loaded,
                    period = period,
                    onPeriodChange = { period = it },
                    onMosqueClick = { selectedMosqueId = it },
                )
            }
        }
    }

    val selectedMosque = state?.stats?.perMosque?.find { it.id == selectedMosqueId }
    if (selectedMosque != null) {
        state?.let { loaded ->
            MosqueVisitSheet(
                mosque = selectedMosque,
                state = loaded,
                period = period,
                onDismiss = { selectedMosqueId = null },
            )
        }
    }
}

@Composable
private fun HistoryContent(
    state: HistoryState,
    period: HistoryPeriod,
    onPeriodChange: (HistoryPeriod) -> Unit,
    onMosqueClick: (Long) -> Unit,
) {
    val entries = remember(state, period) { state.entries(period) }
    val mosques = remember(state, period) { state.mosques(period) }
    LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Your mosque visits",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Where you went. How often you returned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "totals") {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile(
                    title = "Today",
                    count = state.stats.today,
                    detail = state.today.format(DateTimeFormatter.ofPattern("EEE, d MMM", LocalLocale.current.platformLocale)),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    foreground = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SummaryTile(
                    title = "This week",
                    count = state.stats.thisWeek,
                    detail = weekRange(state.stats.weekDays),
                    container = MaterialTheme.colorScheme.primary,
                    foreground = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item(key = "week") { WeeklyActivity(state.stats, state.today) }
        item(key = "period") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Explore visits", style = MaterialTheme.typography.titleLarge)
                    Icon(AppIcons.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    HistoryPeriod.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = period == option,
                            onClick = { onPeriodChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, HistoryPeriod.entries.size),
                            modifier = Modifier.fillMaxHeight().heightIn(min = 48.dp),
                        ) {
                            Text(option.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = AppIcons.Place,
                    title = when {
                        state.history.isEmpty() -> "Your first visit starts here"
                        period == HistoryPeriod.TODAY -> "No visits today yet"
                        else -> "No visits this week yet"
                    },
                    message = if (state.history.isEmpty()) {
                        "Save a mosque on Home and turn on detection. Your visits will appear here automatically."
                    } else {
                        "Try All time to look back at your earlier visits."
                    },
                )
            }
        } else {
            item(key = "mosques-heading") {
                SectionHeading("By mosque", "${visitCount(entries.size)} · ${mosqueCount(mosques.size)}")
            }
            items(mosques, key = { "mosque-${it.id}" }, contentType = { "mosque" }) { mosque ->
                MosqueSummaryRow(mosque, period, onClick = { onMosqueClick(mosque.id) })
            }
            item(key = "timeline-heading") {
                SectionHeading("Visit timeline", "${period.label} · newest first")
            }
            visitItems(entries, state.today, state.zone)
        }
    }
}

@Composable
private fun SummaryTile(
    title: String,
    count: Int,
    detail: String,
    container: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = container, contentColor = foreground) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                formatCount(count),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(if (count == 1) "visit" else "visits", style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun WeeklyActivity(stats: VisitStats, today: LocalDate) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("This week, day by day", style = MaterialTheme.typography.titleMedium)
            if (LocalDensity.current.fontScale > 1.3f) {
                stats.weekDays.forEach { day ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (day.date == today) "Today" else day.date.dayOfWeek.getDisplayName(TextStyle.FULL, LocalLocale.current.platformLocale),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (day.date == today) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            if (day.date > today) "Upcoming" else visitCount(day.count),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val maxCount = maxOf(1, stats.weekDays.maxOf { it.count })
                stats.weekDays.forEach { day ->
                    val isToday = day.date == today
                    val future = day.date > today
                    val description = "${day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(LocalLocale.current.platformLocale))}, " +
                        if (future) "upcoming" else "${visitCount(day.count)}${if (isToday) ", today" else ""}"
                    Column(
                        modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (future) "—" else formatCount(day.count),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.width(18.dp)
                                    .height(if (day.count == 0) 3.dp else (48f * day.count / maxCount).coerceAtLeast(6f).dp)
                                    .background(
                                        when {
                                            isToday -> MaterialTheme.colorScheme.secondary
                                            day.count > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        },
                                        RoundedCornerShape(6.dp),
                                    ),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isToday) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        ) {
                            Text(
                                if (isToday) "Today" else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "${visitCount(stats.total)} all time  ·  ${formatCount(stats.dayStreak)}-day streak",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MosqueSummaryRow(mosque: MosqueVisits, period: HistoryPeriod, onClick: () -> Unit) {
    val description = "${mosque.name}, mosque ${mosque.id}, ${visitCount(mosque.count(period))} ${period.label.lowercase()}. View visits."
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    AppIcons.Place,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(mosque.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Mosque #${mosque.id} · View visits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCount(mosque.count(period)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (mosque.count(period) == 1) "visit" else "visits",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.visitItems(entries: List<AttendanceWithMosque>, today: LocalDate, zone: ZoneId) {
    entries.groupBy { it.visitDate(zone) }.forEach { (date, visits) ->
        item(key = "day-$date", contentType = "date") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    dayLabel(date, today),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(visitCount(visits.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visits, key = { "visit-${it.log.id}" }, contentType = { "visit" }) { HistoryRow(it, zone) }
    }
}

@Composable
private fun HistoryRow(entry: AttendanceWithMosque, zone: ZoneId) {
    val method = runCatching { DetectionMethod.valueOf(entry.log.detectionMethod) }
        .getOrDefault(DetectionMethod.GEOFENCE)
    val ongoing = entry.log.exitTime == null
    val locale = LocalLocale.current.platformLocale
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone)
    val start = timeFormatter.format(Instant.ofEpochMilli(entry.log.entryTime))
    val end = entry.log.exitTime?.let { exit ->
        val instant = Instant.ofEpochMilli(exit)
        if (instant.atZone(zone).toLocalDate() == entry.visitDate(zone)) {
            timeFormatter.format(instant)
        } else {
            DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).withZone(zone).format(instant)
        }
    } ?: "Now"
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                entry.mosqueName ?: "Deleted mosque",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Timer, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "$start → $end",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Mosque #${entry.log.mosqueId} · ${method.displayName()}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = CircleShape,
                    color = if (ongoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (ongoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        if (ongoing) "Ongoing" else TimeFormat.duration(entry.log.entryTime, entry.log.exitTime),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MosqueVisitSheet(mosque: MosqueVisits, state: HistoryState, period: HistoryPeriod, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mosque visits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Close mosque visits") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "mosque-title") {
                SectionHeading(mosque.name, "Mosque #${mosque.id} · ${period.label} · ${visitCount(mosque.count(period))}")
            }
            visitItems(state.entries(period, mosque.id), state.today, state.zone)
        }
    }
}

@Composable
private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale).format(count)
@Composable
private fun visitCount(count: Int): String = "${formatCount(count)} ${if (count == 1) "visit" else "visits"}"
@Composable
private fun mosqueCount(count: Int): String = "${formatCount(count)} ${if (count == 1) "mosque" else "mosques"}"
@Composable
private fun weekRange(days: List<DailyVisits>): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM", LocalLocale.current.platformLocale)
    return "${days.first().date.format(formatter)} – ${days.last().date.format(formatter)}"
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", LocalLocale.current.platformLocale))
}

private fun buildCsv(history: List<AttendanceWithMosque>): String = buildString {
    appendLine("mosque,entry,exit,duration_minutes,method")
    history.forEach { entry ->
        val duration = entry.log.exitTime?.let { ((it - entry.log.entryTime) / 60_000L).coerceAtLeast(0L) } ?: ""
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
