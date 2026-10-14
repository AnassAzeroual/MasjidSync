package com.mosquetracker.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mosquetracker.app.data.local.entity.MosqueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MosqueDao {

    @Query("SELECT * FROM mosques ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MosqueEntity>>

    @Query("SELECT * FROM mosques ORDER BY created_at DESC")
    suspend fun getAll(): List<MosqueEntity>

    @Query("SELECT * FROM mosques WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MosqueEntity?

    @Query("SELECT COUNT(*) FROM mosques")
    fun observeCount(): Flow<Int>

    /**
     * Cheap rectangular pre-filter for the Tier 3 distance fallback. Android's
     * bundled SQLite has no trig functions, so the exact Haversine distance is
     * computed in Kotlin on this small candidate set.
     */
    @Query(
        """
        SELECT * FROM mosques
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
        """,
    )
    suspend fun getWithinBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<MosqueEntity>

    @Insert
    suspend fun insert(mosque: MosqueEntity): Long

    @Update
    suspend fun update(mosque: MosqueEntity)

    @Delete
    suspend fun delete(mosque: MosqueEntity)
}
