package com.mosquetracker.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How the Home map picker should open. */
sealed interface PickRequest {
    /** Drop the pin at the centre of whatever the camera is showing. */
    data object MapCenter : PickRequest

    /** Drop the pin on the current GPS fix (and move the camera there). */
    data object CurrentFix : PickRequest

    /** Fly the camera to a searched place and drop the pin there. */
    data class AtLocation(val latitude: Double, val longitude: Double) : PickRequest
}

/**
 * One-shot hand-off between another screen's "add mosque" action and the Home map's picker.
 *
 * Settings cannot configure the Home screen, and passing a navigation argument would leak
 * "we are mid-edit" into the back stack (rotating the device would replay it). Instead the
 * caller raises this request and switches tabs; Home opens its picker as soon as it sees
 * the request and immediately [consume]s it.
 */
@Singleton
class MosquePickRequest @Inject constructor() {

    private val _requested = MutableStateFlow<PickRequest?>(null)
    val requested: StateFlow<PickRequest?> = _requested.asStateFlow()

    fun request(how: PickRequest = PickRequest.MapCenter) {
        _requested.value = how
    }

    fun consume() {
        _requested.value = null
    }
}
