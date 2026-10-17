package com.mosquetracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mosquetracker.app.model.AlertMode

/**
 * A saved mosque. Column names follow the schema in the project spec so the
 * SQLite layout is predictable and easy to inspect with `sqlite3`.
 */
@Entity(tableName = "mosques")
data class MosqueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    @ColumnInfo(name = "geofence_radius")
    val geofenceRadius: Int = DEFAULT_RADIUS_METERS,
    /**
     * Per-mosque tracking switch (Settings → Location & Geofencing). Untouched by bulk
     * enable/disable: the global monitoring switch gates the service, this gates the mosque.
     */
    @ColumnInfo(name = "tracking_enabled")
    val trackingEnabled: Boolean = true,
    /** Per-mosque arrival behaviour; see [AlertMode]. */
    @ColumnInfo(name = "alert_mode")
    val alertMode: AlertMode = AlertMode.FULL_SILENT,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 100
    }
}
