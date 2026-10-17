package com.mosquetracker.app.ui.qibla

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.KeyValueRow
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import com.mosquetracker.app.util.TimeFormat
import kotlin.math.min

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

    ScreenScaffold(title = "Qibla", modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Qibla compass") {
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
                        text = "${bearing.toInt()}° $label · ${formatKm(distanceMeters)} to Makkah",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        text = "Turn until the green needle points straight up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            SectionCard(title = "Today's prayers") {
                val t = times
                if (t == null) {
                    Text(
                        text = "Waiting for GPS…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    t.ordered().forEach { (name, start) ->
                        val isNext = next?.name == name
                        KeyValueRow(
                            label = if (isNext) "▶ $name" else name,
                            value = TimeFormat.time(start),
                        )
                    }
                    next?.let {
                        Text(
                            text = "${it.name} ${formatCountdown(it.startMillis)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            SectionCard(title = "Prayer silence") {
                SwitchRow(
                    title = "Silence around each prayer",
                    subtitle = "Same sound profile as mosque arrival, ±$bufferMinutes min",
                    checked = prayerSilent,
                    onCheckedChange = viewModel::setPrayerSilent,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = bufferMinutes == minutes,
                            onClick = { viewModel.setBufferMinutes(minutes) },
                            label = { Text("±$minutes min") },
                        )
                    }
                }
                Text(
                    text = "Needs Do Not Disturb access (same as arrival silence). Fires even with the app closed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Muslim World League · Asr Standard · offline math, no network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Compass rose: the dial counter-rotates with the phone heading so north stays true,
 * and the green needle marks the Qibla bearing. No sensors here — pure drawing.
 */
@Composable
private fun CompassDial(azimuthDegrees: Float, qiblaBearing: Float) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val needle = MaterialTheme.colorScheme.primary
    val north = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(cx, cy) - 8f
        val center = Offset(cx, cy)

        drawCircle(color = outline, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))

        // Tick marks every 15°.
        for (degrees in 0 until 360 step 15) {
            rotate(degrees.toFloat(), pivot = center) {
                val major = degrees % 90 == 0
                drawLine(
                    color = outline,
                    start = Offset(cx, cy - radius),
                    end = Offset(cx, cy - radius + if (major) 22f else 12f),
                    strokeWidth = if (major) 5f else 3f,
                )
            }
        }

        rotate(-azimuthDegrees, pivot = center) {
            // Cardinal letters stay glued to true directions.
            drawCardinal("N", 0f, cx, cy, radius, north)
            drawCardinal("E", 90f, cx, cy, radius, outline)
            drawCardinal("S", 180f, cx, cy, radius, outline)
            drawCardinal("W", 270f, cx, cy, radius, outline)

            // Qibla needle at its bearing.
            rotate(qiblaBearing, pivot = center) {
                drawLine(
                    color = needle,
                    start = center,
                    end = Offset(cx, cy - radius + 30f),
                    strokeWidth = 10f,
                )
                drawCircle(color = needle, radius = 14f, center = Offset(cx, cy - radius + 30f))
            }
        }
        drawCircle(color = needle, radius = 8f, center = center)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCardinal(
    letter: String,
    angleDegrees: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
) {
    rotate(angleDegrees, pivot = Offset(cx, cy)) {
        drawContext.canvas.nativeCanvas.drawText(
            letter,
            cx,
            cy - radius + 52f,
            Paint().apply {
                this.color = android.graphics.Color.argb(
                    (color.alpha * 255).toInt(),
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt(),
                )
                textAlign = Paint.Align.CENTER
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
            },
        )
    }
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
