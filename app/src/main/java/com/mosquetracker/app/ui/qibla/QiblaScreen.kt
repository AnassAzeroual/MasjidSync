package com.mosquetracker.app.ui.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import com.mosquetracker.app.util.TimeFormat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun QiblaScreen(
    modifier: Modifier = Modifier,
    viewModel: QiblaViewModel = hiltViewModel(),
) {
    val azimuth by viewModel.azimuth.collectAsStateWithLifecycle()
    val fix by viewModel.location.collectAsStateWithLifecycle()
    val times by viewModel.times.collectAsStateWithLifecycle()
    val next by viewModel.nextPrayer.collectAsStateWithLifecycle()
    val prayerSilent by viewModel.prayerSilentEnabled.collectAsStateWithLifecycle()
    val bufferMinutes by viewModel.bufferMinutes.collectAsStateWithLifecycle()

    val qibla = QiblaViewModel.qiblaFor(fix)

    LifecycleStartEffect(viewModel) {
        viewModel.startLocationUpdates()
        onStopOrDispose { viewModel.stopLocationUpdates() }
    }

    ScreenScaffold(title = "Qibla", modifier = modifier) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Find your direction",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Face Makkah, follow your prayer times, and make room for a quiet moment.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionCard(title = "Direction to Makkah") {
                    val az = azimuth
                    if (az == null) {
                        EmptyState(
                            icon = AppIcons.Compass,
                            title = "No compass sensor",
                            message = "This device has no magnetometer, so live heading is unavailable. Prayer times below still work with GPS.",
                        )
                    } else if (qibla == null) {
                        EmptyState(
                            icon = AppIcons.MyLocation,
                            title = "Waiting for GPS…",
                            message = "The compass needs your position to point at Makkah.",
                        )
                    } else {
                        val (bearing, distanceMeters, label) = qibla
                        CompassDial(azimuthDegrees = az, qiblaBearing = bearing.toFloat())
                        Text(
                            text = "${bearing.toInt()}° $label",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${formatKm(distanceMeters)} to Makkah",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Hold your phone flat. Turn until the pointed needle faces the top of your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SectionCard(title = "Today's prayers") {
                    val t = times
                    if (t == null) {
                        Text(
                            text = "Waiting for GPS…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        next?.let {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {},
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Next prayer", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = if (it.tomorrow) "Fajr (tomorrow)" else it.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    Text(
                                        text = TimeFormat.time(it.startMillis),
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                    Text(
                                        text = formatCountdown(it.startMillis),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                        t.ordered().forEach { (name, start) ->
                            val isNext = next?.let { !it.tomorrow && it.name == name } == true
                            PrayerTimeRow(name = name, startMillis = start, isNext = isNext)
                        }
                    }
                }

                SectionCard(title = "Prayer silence") {
                    SwitchRow(
                        title = "Silence around each prayer",
                        subtitle = "Use your mosque-arrival sound profile for $bufferMinutes min before and after each prayer.",
                        checked = prayerSilent,
                        onCheckedChange = viewModel::setPrayerSilent,
                    )
                    Text(
                        text = "Time before and after",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 15, 30).forEach { minutes ->
                            FilterChip(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics {
                                        contentDescription = "$minutes minutes before and after each prayer"
                                    },
                                selected = bufferMinutes == minutes,
                                onClick = { viewModel.setBufferMinutes(minutes) },
                                leadingIcon = if (bufferMinutes == minutes) {
                                    { Icon(AppIcons.Check, contentDescription = null) }
                                } else null,
                                label = { Text("±$minutes min") },
                            )
                        }
                    }
                    Text(
                        text = "Needs Do Not Disturb access, just like arrival silence. Works even when the app is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "About these times",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "Online: Aladhan, Morocco method (Fajr 19°, Isha 17°).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Offline: calculated on your phone, Muslim World League (Fajr 18°, Isha 17°). Asr Standard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerTimeRow(name: String, startMillis: Long, isNext: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = if (isNext) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isNext) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                if (isNext) Text(text = "Next", style = MaterialTheme.typography.labelMedium)
            }
            Text(text = TimeFormat.time(startMillis), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CompassDial(azimuthDegrees: Float, qiblaBearing: Float) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface)
    val cardinals = remember(textMeasurer, cardinalStyle, colors.primary) {
        listOf("N", "E", "S", "W").map { letter ->
            textMeasurer.measure(
                text = letter,
                style = if (letter == "N") cardinalStyle.copy(color = colors.primary) else cardinalStyle,
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics {
                    role = Role.Image
                    contentDescription = "Qibla compass. Device heading ${azimuthDegrees.toInt()} degrees. " +
                        "Qibla bearing ${qiblaBearing.toInt()} degrees from north. " +
                        "Turn until the pointed needle faces the top of your phone."
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(center.x, center.y) - 12.dp.toPx()
            val labelSize = cardinals.maxOf { maxOf(it.size.width, it.size.height) }.toFloat()
            val labelRadius = (radius - 16.dp.toPx() - labelSize / 2f).coerceAtLeast(0f)
            val needleRadius = (labelRadius - labelSize / 2f - 8.dp.toPx()).coerceAtLeast(radius * 0.25f)

            drawCircle(color = colors.surfaceContainerLow, radius = radius)
            drawCircle(color = colors.outlineVariant, radius = radius, style = Stroke(1.dp.toPx()))
            drawCircle(
                color = colors.outlineVariant,
                radius = needleRadius + 4.dp.toPx(),
                style = Stroke(1.dp.toPx()),
            )
            drawLine(
                color = colors.primary,
                start = Offset(center.x, center.y - radius - 8.dp.toPx()),
                end = Offset(center.x, center.y - radius - 2.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )

            rotate(-azimuthDegrees, pivot = center) {
                for (degrees in 0 until 360 step 15) {
                    rotate(degrees.toFloat(), pivot = center) {
                        val major = degrees % 90 == 0
                        drawLine(
                            color = if (major) colors.onSurfaceVariant else colors.outlineVariant,
                            start = Offset(center.x, center.y - radius + 4.dp.toPx()),
                            end = Offset(center.x, center.y - radius + if (major) 12.dp.toPx() else 8.dp.toPx()),
                            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                rotate(qiblaBearing, pivot = center) {
                    drawLine(
                        color = colors.onSurfaceVariant,
                        start = center,
                        end = Offset(center.x, center.y + needleRadius * 0.6f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    val pointer = Path().apply {
                        moveTo(center.x, center.y - needleRadius)
                        lineTo(center.x + 8.dp.toPx(), center.y + 8.dp.toPx())
                        lineTo(center.x, center.y + 3.dp.toPx())
                        lineTo(center.x - 8.dp.toPx(), center.y + 8.dp.toPx())
                        close()
                    }
                    drawPath(path = pointer, color = colors.primary)
                }
            }
            cardinals.forEachIndexed { index, label ->
                drawCardinal(label, index * 90f - azimuthDegrees, center, labelRadius)
            }
            drawCircle(color = colors.primary, radius = 6.dp.toPx(), center = center)
            drawCircle(color = colors.onPrimary, radius = 2.dp.toPx(), center = center)
        }
    }
}

private fun DrawScope.drawCardinal(
    label: TextLayoutResult,
    angleDegrees: Float,
    center: Offset,
    radius: Float,
) {
    val radians = Math.toRadians(angleDegrees.toDouble())
    drawText(
        textLayoutResult = label,
        topLeft = Offset(
            x = center.x + sin(radians).toFloat() * radius - label.size.width / 2f,
            y = center.y - cos(radians).toFloat() * radius - label.size.height / 2f,
        ),
    )
}

private fun formatKm(meters: Double): String =
    if (meters >= 100_000) {
        "%,d".format((meters / 1_000).toInt()) + " km"
    } else {
        "%.0f".format(meters / 1_000) + " km"
    }

private fun formatCountdown(startMillis: Long): String {
    val minutes = ((startMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60
    return when {
        hours > 0 -> "in ${hours}h ${minutes % 60}m"
        minutes > 0 -> "in ${minutes}m"
        else -> "now"
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
