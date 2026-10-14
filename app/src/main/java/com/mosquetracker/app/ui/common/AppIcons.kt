package com.mosquetracker.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of Material Symbols this app needs, defined locally as [ImageVector]s.
 *
 * The official `androidx.compose.material:material-icons-core` / `-extended` artifacts are
 * frozen (their last release was 1.7.8) and no longer maintained, so we ship our own instead of
 * pulling in an unmaintained dependency. Paths are the standard 24dp Material glyphs; [Icon]
 * tints them with the current content color at the call site.
 */
object AppIcons {
    val Add: ImageVector by lazy {
        materialIcon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }

    val Home: ImageVector by lazy {
        materialIcon("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    }

    val DateRange: ImageVector by lazy {
        materialIcon(
            "DateRange",
            "M9,11H7v2h2V11zM13,11h-2v2h2V11zM17,11h-2v2h2V11zM19,4h-1V2h-2v2H8V2H6v2H5" +
                "C3.89,4 3.01,4.9 3.01,6L3,20c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4z" +
                "M19,20H5V9h14V20z",
        )
    }

    val Delete: ImageVector by lazy {
        materialIcon(
            "Delete",
            "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z",
        )
    }

    val LocationOn: ImageVector by lazy {
        materialIcon(
            "LocationOn",
            "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13C19,5.13 15.87,2 12,2z" +
                "M12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
        )
    }

    val Place: ImageVector by lazy {
        materialIcon(
            "Place",
            "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13C19,5.13 15.87,2 12,2z" +
                "M12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
        )
    }

    val Refresh: ImageVector by lazy {
        materialIcon(
            "Refresh",
            "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -8,8s3.58,8 8,8c3.73,0 6.84,-2.55 7.73,-6h-2.08" +
                "c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z",
        )
    }
}

private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()
