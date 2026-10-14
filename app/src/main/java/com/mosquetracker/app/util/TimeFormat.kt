package com.mosquetracker.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Locale-aware-ish formatting helpers built on java.time (API 26+). */
object TimeFormat {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

    fun date(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))

    fun dateTime(epochMillis: Long): String = "${date(epochMillis)} · ${time(epochMillis)}"

    fun duration(startMillis: Long, endMillis: Long?): String {
        val end = endMillis ?: System.currentTimeMillis()
        val totalMinutes = ((end - startMillis) / 60_000L).coerceAtLeast(0L)
        if (endMillis == null) return "in progress"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    fun startOfTodayMillis(): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
}
