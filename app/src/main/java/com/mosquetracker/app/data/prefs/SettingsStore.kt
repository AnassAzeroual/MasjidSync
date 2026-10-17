package com.mosquetracker.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mosquetracker.app.model.RoutineAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small persisted settings store (DataStore). */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val AUTO_SILENT = booleanPreferencesKey("auto_silent")
        val ROUTINE_ACTIONS = stringPreferencesKey("routine_actions")
        val ROUTINE_SNAPSHOT = stringPreferencesKey("routine_snapshot")
        val PRAYER_SILENT_ENABLED = booleanPreferencesKey("prayer_silent_enabled")
        val PRAYER_BUFFER_MINUTES = intPreferencesKey("prayer_buffer_minutes")
        val PRAYER_CACHE_DATE = stringPreferencesKey("prayer_cache_date")
        val PRAYER_CACHE_JSON = stringPreferencesKey("prayer_cache_json")
        val PRAYER_CACHE_TZ = stringPreferencesKey("prayer_cache_tz")
        val PRAYER_CACHE_LAT = stringPreferencesKey("prayer_cache_lat")
        val PRAYER_CACHE_LON = stringPreferencesKey("prayer_cache_lon")
        val PRAYER_CACHE_METHOD = intPreferencesKey("prayer_cache_method")
    }

    /** Last successfully fetched prayer times, so today still shows something offline. */
    data class PrayerCache(
        val dateIso: String,
        val times: Map<String, String>,
        val timeZoneId: String?,
        val latitude: Double,
        val longitude: Double,
        val method: Int,
    )

    suspend fun readPrayerCache(): PrayerCache? {
        val prefs = context.dataStore.data.first()
        val date = prefs[Keys.PRAYER_CACHE_DATE] ?: return null
        val json = prefs[Keys.PRAYER_CACHE_JSON] ?: return null
        val lat = prefs[Keys.PRAYER_CACHE_LAT]?.toDoubleOrNull() ?: return null
        val lon = prefs[Keys.PRAYER_CACHE_LON]?.toDoubleOrNull() ?: return null
        val map = runCatching {
            JSONObject(json).let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            }
        }.getOrNull() ?: return null
        return PrayerCache(date, map, prefs[Keys.PRAYER_CACHE_TZ], lat, lon, prefs[Keys.PRAYER_CACHE_METHOD] ?: -1)
    }

    suspend fun writePrayerCache(cache: PrayerCache) {
        context.dataStore.edit {
            it[Keys.PRAYER_CACHE_DATE] = cache.dateIso
            it[Keys.PRAYER_CACHE_JSON] = JSONObject(cache.times).toString()
            it.remove(Keys.PRAYER_CACHE_TZ)
            cache.timeZoneId?.let { tz -> it[Keys.PRAYER_CACHE_TZ] = tz }
            it[Keys.PRAYER_CACHE_LAT] = cache.latitude.toString()
            it[Keys.PRAYER_CACHE_LON] = cache.longitude.toString()
            it[Keys.PRAYER_CACHE_METHOD] = cache.method
        }
    }

    val monitoringEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MONITORING_ENABLED] ?: false }

    val autoSilent: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_SILENT] ?: true }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setAutoSilent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SILENT] = enabled }
    }

    suspend fun isMonitoringEnabled(): Boolean = monitoringEnabled.first()

    suspend fun isAutoSilentEnabled(): Boolean = autoSilent.first()

    /**
     * Which routine actions the user has armed. Missing key (fresh install, or an install that
     * predates the editable routine) means "the defaults", not "nothing".
     */
    val routineActions: Flow<Set<RoutineAction>> =
        context.dataStore.data.map { prefs ->
            decodeActions(prefs[Keys.ROUTINE_ACTIONS]) ?: RoutineAction.DEFAULT_ENABLED
        }

    suspend fun getRoutineActions(): Set<RoutineAction> = routineActions.first()

    suspend fun setRoutineAction(action: RoutineAction, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = decodeActions(prefs[Keys.ROUTINE_ACTIONS]) ?: RoutineAction.DEFAULT_ENABLED
            val next = if (enabled) current + action else current - action
            prefs[Keys.ROUTINE_ACTIONS] = next.joinToString(",") { it.name }
        }
    }

    private fun decodeActions(raw: String?): Set<RoutineAction>? {
        if (raw == null) return null
        val known = RoutineAction.values().associateBy { it.name }
        return raw.split(",").mapNotNullTo(mutableSetOf()) { known[it.trim()] }
    }

    /**
     * The pre-arrival device state captured when the routine first ran, so the exit handler can
     * put everything back even if the process died in between. Null means "nothing applied".
     */
    suspend fun readRoutineSnapshot(): RoutineSnapshot? {
        val raw = context.dataStore.data.first()[Keys.ROUTINE_SNAPSHOT] ?: return null
        return RoutineSnapshot.fromJson(raw)
    }

    suspend fun writeRoutineSnapshot(snapshot: RoutineSnapshot) {
        context.dataStore.edit { it[Keys.ROUTINE_SNAPSHOT] = snapshot.toJson() }
    }

    suspend fun clearRoutineSnapshot() {
        context.dataStore.edit { it.remove(Keys.ROUTINE_SNAPSHOT) }
    }

    val routineSnapshot: Flow<RoutineSnapshot?> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.ROUTINE_SNAPSHOT]?.let(RoutineSnapshot::fromJson)
        }

    val prayerSilentEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PRAYER_SILENT_ENABLED] ?: false }

    suspend fun setPrayerSilentEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRAYER_SILENT_ENABLED] = enabled }
    }

    suspend fun isPrayerSilentEnabled(): Boolean = prayerSilentEnabled.first()

    val prayerBufferMinutes: Flow<Int> =
        context.dataStore.data.map { it[Keys.PRAYER_BUFFER_MINUTES] ?: DEFAULT_PRAYER_BUFFER_MINUTES }

    suspend fun setPrayerBufferMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.PRAYER_BUFFER_MINUTES] = minutes }
    }

    suspend fun getPrayerBufferMinutes(): Int = prayerBufferMinutes.first()

    companion object {
        const val DEFAULT_PRAYER_BUFFER_MINUTES = 15
    }
}

/**
 * Everything the routine changed on entry, captured once so it can be put back on exit.
 * A field left at [UNSET] means "we never touched this".
 */
data class RoutineSnapshot(
    /** Names of the actions that were active when this snapshot was taken. */
    val actions: Set<String> = emptySet(),
    val ringerMode: Int = UNSET,
    val musicVolume: Int = UNSET,
    val alarmVolume: Int = UNSET,
    val notificationVolume: Int = UNSET,
    val systemVolume: Int = UNSET,
    val dndFilter: Int = UNSET,
    val brightness: Int = UNSET,
    val brightnessMode: Int = UNSET,
    val screenTimeout: Int = UNSET,
    val autoRotate: Int = UNSET,
    val flashlight: Boolean = false,
) {
    /** True while at least one changed setting is still waiting to be restored. */
    fun hasPendingRestore(): Boolean =
        flashlight || listOf(
            ringerMode,
            musicVolume,
            alarmVolume,
            notificationVolume,
            systemVolume,
            dndFilter,
            brightness,
            brightnessMode,
            screenTimeout,
            autoRotate,
        ).any { it != UNSET }

    /**
     * Fills in the fields we never managed to capture, from [fresh] — a capture read from the live
     * state a moment ago. Only [UNSET] fields are touched, and unset only ever means the action was
     * armed while Android had not granted the access it needs, so nothing was written and the value
     * read now is still the true pre-arrival one.
     *
     * Without this, granting Do Not Disturb or "Modify system settings" after the first arrival
     * would leave those fields unset for good: the routine would silence, dim and lock the rotation
     * on every later arrival but never put any of it back.
     *
     * @param skipSystemVolume leaves the system stream alone when a ring-mode action is what muted
     *  the streams. The ring mode puts those back itself, and reading the already-zeroed value in
     *  would leave the user's touch sounds muted after exit.
     */
    fun fillGaps(fresh: RoutineSnapshot, skipSystemVolume: Boolean): RoutineSnapshot {
        fun fill(stored: Int, live: Int): Int = if (stored == UNSET) live else stored
        return copy(
            ringerMode = fill(ringerMode, fresh.ringerMode),
            musicVolume = fill(musicVolume, fresh.musicVolume),
            alarmVolume = fill(alarmVolume, fresh.alarmVolume),
            notificationVolume = fill(notificationVolume, fresh.notificationVolume),
            systemVolume = if (skipSystemVolume) {
                systemVolume
            } else {
                fill(systemVolume, fresh.systemVolume)
            },
            dndFilter = fill(dndFilter, fresh.dndFilter),
            brightness = fill(brightness, fresh.brightness),
            brightnessMode = fill(brightnessMode, fresh.brightnessMode),
            screenTimeout = fill(screenTimeout, fresh.screenTimeout),
            autoRotate = fill(autoRotate, fresh.autoRotate),
            flashlight = flashlight || fresh.flashlight,
        )
    }

    fun toJson(): String = JSONObject().apply {
        put(KEY_ACTIONS, actions.joinToString(","))
        put(KEY_RINGER, ringerMode)
        put(KEY_MUSIC, musicVolume)
        put(KEY_ALARM, alarmVolume)
        put(KEY_NOTIFICATION, notificationVolume)
        put(KEY_SYSTEM, systemVolume)
        put(KEY_DND, dndFilter)
        put(KEY_BRIGHTNESS, brightness)
        put(KEY_BRIGHTNESS_MODE, brightnessMode)
        put(KEY_SCREEN_TIMEOUT, screenTimeout)
        put(KEY_AUTO_ROTATE, autoRotate)
        put(KEY_FLASHLIGHT, flashlight)
    }.toString()

    companion object {
        const val UNSET = -1

        private const val KEY_ACTIONS = "actions"
        private const val KEY_RINGER = "ringer"
        private const val KEY_MUSIC = "music"
        private const val KEY_ALARM = "alarm"
        private const val KEY_NOTIFICATION = "notification"
        private const val KEY_SYSTEM = "system"
        private const val KEY_DND = "dnd"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_BRIGHTNESS_MODE = "brightness_mode"
        private const val KEY_SCREEN_TIMEOUT = "screen_timeout"
        private const val KEY_AUTO_ROTATE = "auto_rotate"
        private const val KEY_FLASHLIGHT = "flashlight"

        fun fromJson(raw: String): RoutineSnapshot? = runCatching {
            val json = JSONObject(raw)
            RoutineSnapshot(
                actions = json.optString(KEY_ACTIONS, "")
                    .split(",")
                    .filterTo(mutableSetOf()) { it.isNotBlank() },
                ringerMode = json.optInt(KEY_RINGER, UNSET),
                musicVolume = json.optInt(KEY_MUSIC, UNSET),
                alarmVolume = json.optInt(KEY_ALARM, UNSET),
                notificationVolume = json.optInt(KEY_NOTIFICATION, UNSET),
                systemVolume = json.optInt(KEY_SYSTEM, UNSET),
                dndFilter = json.optInt(KEY_DND, UNSET),
                brightness = json.optInt(KEY_BRIGHTNESS, UNSET),
                brightnessMode = json.optInt(KEY_BRIGHTNESS_MODE, UNSET),
                screenTimeout = json.optInt(KEY_SCREEN_TIMEOUT, UNSET),
                autoRotate = json.optInt(KEY_AUTO_ROTATE, UNSET),
                flashlight = json.optBoolean(KEY_FLASHLIGHT, false),
            )
        }.getOrNull()
    }
}
