package com.mosquetracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.FenceState
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.routine.RoutineCoordinator
import com.mosquetracker.app.routine.RoutineEngine
import com.mosquetracker.app.ui.common.MosquePickRequest
import com.mosquetracker.app.ui.common.PickRequest
import com.mosquetracker.app.util.PlaceSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Special accesses the routine needs; surfaced so the screen can explain what is missing. */
data class AccessState(
    val dnd: Boolean = false,
    val writeSettings: Boolean = false,
)

/** Backs Settings: the routine editor, the tracking toggles, mosque search and monitoring. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val monitoringController: MonitoringController,
    private val settingsStore: SettingsStore,
    private val routineEngine: RoutineEngine,
    private val routineCoordinator: RoutineCoordinator,
    private val pickRequest: MosquePickRequest,
    private val placeSearch: PlaceSearch,
) : ViewModel() {

    val mosques: StateFlow<List<MosqueEntity>> = mosqueRepository.mosques
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monitoringEnabled: StateFlow<Boolean> = settingsStore.monitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoSilent: StateFlow<Boolean> = settingsStore.autoSilent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The armed actions, i.e. the editable "Then" / "When it ends" lists. */
    val routineActions: StateFlow<Set<RoutineAction>> = settingsStore.routineActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineAction.DEFAULT_ENABLED)

    /** True while the routine has changed something it has not put back yet. */
    val restorePending: StateFlow<Boolean> = settingsStore.routineSnapshot
        .map { it?.hasPendingRestore() == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _access = MutableStateFlow(AccessState())
    val access: StateFlow<AccessState> = _access.asStateFlow()

    /** Which tracked mosques Android is watching with a geofence, and why the rest are not. */
    val fenceState: StateFlow<FenceState?> = monitoringController.fenceState

    private val _online = MutableStateFlow(false)
    /** Whether search is usable right now. Refreshed every time Settings is shown. */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searchMessage = MutableStateFlow<String?>(null)
    val searchMessage: StateFlow<String?> = _searchMessage.asStateFlow()

    fun refreshOnline() {
        _online.value = placeSearch.isOnline()
    }

    /** Re-reads the two special accesses, which the user grants outside the app. */
    fun refreshAccess() {
        _access.value = AccessState(
            dnd = routineEngine.dndAccessGranted,
            writeSettings = routineEngine.writeSettingsGranted,
        )
    }

    /** After a permission prompt: the fences and the service may now be allowed to run. */
    fun onPermissionsChanged() {
        viewModelScope.launch { monitoringController.recheck() }
    }

    fun setRoutineAction(action: RoutineAction, enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setRoutineAction(action, enabled)
            routineCoordinator.reconcile()
        }
    }

    /** Safety valve: put back anything the routine changed, regardless of the exit toggles. */
    fun restoreNow() {
        viewModelScope.launch { routineCoordinator.restoreNow() }
    }

    fun setTracking(mosque: MosqueEntity, enabled: Boolean) {
        viewModelScope.launch {
            monitoringController.setMosqueTracking(mosque.id, enabled)
        }
    }

    /** Cycles a mosque between full silent and notify-only arrival behaviour. */
    fun toggleAlertMode(mosque: MosqueEntity) {
        viewModelScope.launch {
            val next = if (mosque.alertMode == AlertMode.FULL_SILENT) {
                AlertMode.NOTIFY_ONLY
            } else {
                AlertMode.FULL_SILENT
            }
            monitoringController.setAlertMode(mosque.id, next)
        }
    }

    fun deleteMosque(mosque: MosqueEntity) {
        viewModelScope.launch { monitoringController.deleteMosque(mosque) }
    }

    /**
     * Renames a mosque. Zones are auto-named ("Mosque 1", …) on the map, so this is where the
     * real name is given. The name is display-only — the geofence keys off the id, so no
     * re-registration is needed. A blank name is ignored.
     */
    fun renameMosque(mosque: MosqueEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == mosque.name) return
        viewModelScope.launch {
            mosqueRepository.update(mosque.copy(name = trimmed))
        }
    }

    fun setMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) monitoringController.enableMonitoring() else monitoringController.disableMonitoring()
        }
    }

    fun setAutoSilent(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoSilent(enabled)
            routineCoordinator.reconcile()
        }
    }

    /** Hands off to the Home map picker, centred on the current GPS fix. */
    fun useCurrentLocation() {
        pickRequest.request(PickRequest.CurrentFix)
    }

    /**
     * Searches for a place name, then hands off to the Home map picker at the match.
     * Calls [onFound] (which switches tabs) only on a real result; every other outcome
     * surfaces as helper text under the field.
     */
    fun searchPlace(query: String, onFound: () -> Unit) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || _searching.value) return
        viewModelScope.launch {
            _searching.value = true
            _searchMessage.value = null
            when (val result = placeSearch.search(trimmed)) {
                is PlaceSearch.Result.Found -> {
                    pickRequest.request(
                        PickRequest.AtLocation(result.latitude, result.longitude),
                    )
                    onFound()
                }

                is PlaceSearch.Result.NoResults ->
                    _searchMessage.value = "No match for \"$trimmed\" — try a nearby landmark."

                is PlaceSearch.Result.Unavailable ->
                    _searchMessage.value = result.message
            }
            _searching.value = false
        }
    }

    fun clearSearchMessage() {
        _searchMessage.value = null
    }

    init {
        refreshAccess()
    }
}
