package com.mosquetracker.app.model

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * The basemaps the Home map can draw.
 *
 * Both are keyless and free of billing, which is what keeps the app's map usable without an
 * account: the street layer is the OpenStreetMap one osmdroid ships with, and the satellite layer
 * is Esri's World Imagery service.
 *
 * [credit] is not decoration. Both providers give their tiles away on the condition that the
 * attribution stays visible, so the Home map prints it under the map for whichever layer is
 * selected.
 */
enum class MapLayer(
    val label: String,
    val description: String,
    val credit: String,
) {
    STREETS(
        label = "Streets",
        description = "OpenStreetMap roads, names and labels",
        credit = "© OpenStreetMap contributors",
    ),
    SATELLITE(
        label = "Satellite",
        description = "Aerial photos with no labels, from Esri World Imagery",
        credit = "Imagery © Esri, Maxar, Earthstar Geographics",
    ),
    ;

    /** The osmdroid tile source that draws this layer. */
    val tileSource: ITileSource
        get() = when (this) {
            STREETS -> TileSourceFactory.MAPNIK
            SATELLITE -> EsriWorldImagery
        }

    companion object {
        private const val ESRI_BASE_URL =
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile"

        private const val ESRI_CREDIT = "Imagery © Esri, Maxar, Earthstar Geographics"

        /**
         * Esri's World Imagery tiles.
         *
         * osmdroid's ready-made sources all build "{base}/{z}/{x}/{y}" URLs, but this service
         * wants the row before the column ("{z}/{y}/{x}"), so the URL is assembled here instead.
         * Zoom stops at 19, which is where this service's imagery ends.
         */
        private object EsriWorldImagery : OnlineTileSourceBase(
            "EsriWorldImagery",
            0,
            19,
            256,
            ".jpg",
            arrayOf(ESRI_BASE_URL),
            ESRI_CREDIT,
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val row = MapTileIndex.getY(pMapTileIndex)
                val column = MapTileIndex.getX(pMapTileIndex)
                return "$ESRI_BASE_URL/$zoom/$row/$column"
            }
        }

        /**
         * Decodes the stored preference. A missing or unrecognised value means the street layer,
         * so a downgrade back to a build without this preference still opens on a working map.
         */
        fun fromStorage(stored: String?): MapLayer =
            entries.firstOrNull { it.name == stored } ?: STREETS
    }
}
