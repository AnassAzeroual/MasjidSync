package com.mosquetracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    @ColumnInfo(name = "wifi_bssid")
    val wifiBssid: String? = null,
    @ColumnInfo(name = "ble_uuid")
    val bleUuid: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_RADIUS_METERS = 100
    }
}
