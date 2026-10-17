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

    val MyLocation: ImageVector by lazy {
        materialIcon(
            "MyLocation",
            "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM20.94,11" +
                "c-0.46,-4.17 -3.77,-7.48 -7.94,-7.94V1h-2v2.06C6.83,3.52 3.52,6.83 3.06,11H1v2h2.06" +
                "c0.46,4.17 3.77,7.48 7.94,7.94V23h2v-2.06c4.17,-0.46 7.48,-3.77 7.94,-7.94H23v-2h-2.06z" +
                "M12,19c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        )
    }

    val Close: ImageVector by lazy {
        materialIcon(
            "Close",
            "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
        )
    }

    val Settings: ImageVector by lazy {
        materialIcon(
            "Settings",
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
                "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
                "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.5,-0.41h-3.8" +
                "c-0.27,0 -0.46,0.17 -0.5,0.41L9.14,5.35C8.55,5.59 8.02,5.91 7.52,6.29L5.13,5.33" +
                "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.62,8.87c-0.12,0.21 -0.06,0.47 0.12,0.61l2.03,1.58" +
                "C4.72,11.36 4.7,11.68 4.7,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
                "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                "c0.05,0.24 0.24,0.41 0.5,0.41h3.8c0.27,0 0.46,-0.17 0.5,-0.41l0.36,-2.54" +
                "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
                "c0.12,-0.22 0.06,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6" +
                "s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
        )
    }

    val Search: ImageVector by lazy {
        materialIcon(
            "Search",
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5" +
                " 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5z" +
                "M9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
        )
    }

    val CheckCircle: ImageVector by lazy {
        materialIcon(
            "CheckCircle",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z",
        )
    }

    val Edit: ImageVector by lazy {
        materialIcon(
            "Edit",
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41" +
                "l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        )
    }

    val Send: ImageVector by lazy {
        materialIcon(
            "Send",
            "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2L2.01,21z",
        )
    }

    /** Compass rose for the Qibla tab. */
    val Compass: ImageVector by lazy {
        materialIcon(
            "Compass",
            "M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1 1.1,-0.49 1.1,-1.1" +
                " -0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10" +
                "S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z",
        )
    }

    val Download: ImageVector by lazy {
        materialIcon(
            "Download",
            "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z",
        )
    }

    val Silent: ImageVector by lazy {
        materialIcon(
            "Silent",
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63z" +
                "M19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12" +
                "c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5" +
                "v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81" +
                "L19.73,21 21,19.73 4.27,3zM12,4L9.91,6.09 12,8.18V4z",
        )
    }

    /** Bell: arrival/exit notices and the Do-Not-Disturb row. */
    val Notifications: ImageVector by lazy {
        materialIcon(
            "Notifications",
            "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 " +
                "-4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 " +
                "6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z",
        )
    }

    /** Muted speaker: the volume actions in the routine editor. */
    val VolumeOff: ImageVector by lazy {
        materialIcon(
            "VolumeOff",
            "M4.34,2.93L2.93,4.34 7.29,8.7H3v6h4l5,5v-6.59l4.18,4.18c-0.65,0.49 -1.38,0.88 " +
                "-2.18,1.11v2.06c1.27,-0.3 2.42,-0.9 3.39,-1.71l2.25,2.25 1.41,-1.41L4.34,2.93z" +
                "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63z" +
                "M19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12" +
                "c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM12,4L9.91,6.09 12,8.18V4z",
        )
    }

    /** Lightbulb: the flashlight action. */
    val Lightbulb: ImageVector by lazy {
        materialIcon(
            "Lightbulb",
            "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9v1zM12,2C8.14,2 5,5.14 5,9" +
                "c0,2.38 1.19,4.47 3,5.74V17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26" +
                "c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z",
        )
    }

    /** Sun with a small core: the brightness action. */
    val BrightnessLow: ImageVector by lazy {
        materialIcon(
            "BrightnessLow",
            "M20,15.31L23.31,12 20,8.69V4h-4.69L12,0.69 8.69,4H4v4.69L0.69,12 4,15.31V20h4.69" +
                "L12,23.31 15.31,20H20v-4.69zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 " +
                "-2.24,5 -5,5z",
        )
    }

    /** Phone mid-rotation: the auto-rotate lock action. */
    val ScreenRotation: ImageVector by lazy {
        materialIcon(
            "ScreenRotation",
            "M16.48,2.52c3.27,1.55 5.61,4.72 5.97,8.48h1.5C23.44,4.84 18.29,0 12,0l-0.66,0.03 " +
                "4.83,4.83 0.31,-2.34zM10.23,1.75c-0.59,-0.59 -1.54,-0.59 -2.12,0L1.75,8.11" +
                "c-0.59,0.59 -0.59,1.54 0,2.12l12.02,12.02c0.59,0.59 1.54,0.59 2.12,0l6.36,-6.36" +
                "c0.59,-0.59 0.59,-1.54 0,-2.12L10.23,1.75zM14.83,21.19L2.81,9.17l6.36,-6.36 " +
                "12.02,12.02 -6.36,6.36zM7.52,21.48C4.25,19.94 1.91,16.76 1.55,13H0.05" +
                "C0.56,19.16 5.71,24 12,24l0.66,-0.03 -4.83,-4.83 -0.31,2.34z",
        )
    }

    /** Phone between two vibration arcs: the vibrate action. */
    val Vibration: ImageVector by lazy {
        materialIcon(
            "Vibration",
            "M0,15h2V9H0v6zM3,17h2V7H3v10zM22,9v6h2V9h-2zM19,7v10h2V7h-2zM16.5,3h-9" +
                "C6.67,3 6,3.67 6,4.5v15c0,0.83 0.67,1.5 1.5,1.5h9c0.83,0 1.5,-0.67 1.5,-1.5v-15" +
                "C18,3.67 17.33,3 16.5,3zM16,19H8V5h8V19z",
        )
    }

    /** Stopwatch: the screen-timeout action. */
    val Timer: ImageVector by lazy {
        materialIcon(
            "Timer",
            "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 " +
                "-1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 " +
                "9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 " +
                "7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        )
    }

    /** Stacked sheets: the map style switcher on Home. */
    val Layers: ImageVector by lazy {
        materialIcon(
            "Layers",
            "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16" +
                "l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z",
        )
    }

    /** Tick: marks the map style currently in use. */
    val Check: ImageVector by lazy {
        materialIcon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
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
