package com.mosquetracker.app.data.local

import androidx.room.TypeConverter
import com.mosquetracker.app.model.AlertMode

/** Room has no built-in enum mapping; unknown values fall back to full silent. */
class Converters {

    @TypeConverter
    fun alertModeToString(mode: AlertMode): String = mode.name

    @TypeConverter
    fun stringToAlertMode(name: String): AlertMode =
        runCatching { AlertMode.valueOf(name) }.getOrDefault(AlertMode.FULL_SILENT)
}
