package com.mosquetracker.app.data.repository

import com.mosquetracker.app.data.local.MosqueDao
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.detection.GeoUtils
import com.mosquetracker.app.detection.Haversine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for saved mosques. */
@Singleton
class MosqueRepository @Inject constructor(
    private val mosqueDao: MosqueDao,
) {
    val mosques: Flow<List<MosqueEntity>> = mosqueDao.observeAll()
    val mosqueCount: Flow<Int> = mosqueDao.observeCount()

    suspend fun getById(id: Long): MosqueEntity? = mosqueDao.getById(id)

    suspend fun all(): List<MosqueEntity> = mosqueDao.getAll()

    suspend fun add(mosque: MosqueEntity): Long = mosqueDao.insert(mosque)

    suspend fun update(mosque: MosqueEntity) = mosqueDao.update(mosque)

    suspend fun delete(mosque: MosqueEntity) = mosqueDao.delete(mosque)

    /**
     * Nearest saved mosque within [searchRadiusMeters], paired with its exact
     * distance. Uses a bounding-box pre-filter, then Haversine on the candidates.
     */
    suspend fun nearestWithin(
        latitude: Double,
        longitude: Double,
        searchRadiusMeters: Double,
    ): Pair<MosqueEntity, Double>? {
        val latDelta = GeoUtils.latitudeDegreesFor(searchRadiusMeters)
        val lonDelta = GeoUtils.longitudeDegreesFor(searchRadiusMeters, latitude)

        return mosqueDao
            .getWithinBoundingBox(
                minLat = latitude - latDelta,
                maxLat = latitude + latDelta,
                minLon = longitude - lonDelta,
                maxLon = longitude + lonDelta,
            )
            .map { it to Haversine.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { it.second <= searchRadiusMeters }
            .minByOrNull { it.second }
    }
}
