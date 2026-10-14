package com.mosquetracker.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        val PREVIOUS_RINGER_MODE = intPreferencesKey("previous_ringer_mode")
        val SILENT_APPLIED = booleanPreferencesKey("silent_applied")
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

    suspend fun previousRingerMode(): Int =
        context.dataStore.data.first()[Keys.PREVIOUS_RINGER_MODE] ?: NO_RINGER_MODE

    suspend fun setPreviousRingerMode(mode: Int) {
        context.dataStore.edit { it[Keys.PREVIOUS_RINGER_MODE] = mode }
    }

    suspend fun isSilentApplied(): Boolean =
        context.dataStore.data.first()[Keys.SILENT_APPLIED] ?: false

    suspend fun setSilentApplied(applied: Boolean) {
        context.dataStore.edit { it[Keys.SILENT_APPLIED] = applied }
    }

    companion object {
        const val NO_RINGER_MODE = -1
    }
}
