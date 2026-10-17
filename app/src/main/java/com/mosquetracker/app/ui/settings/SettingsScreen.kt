package com.mosquetracker.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.model.ActionGroup
import com.mosquetracker.app.model.ActionRequirement
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.BLOCKED_ACTIONS
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.RequiredPermissions
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import com.mosquetracker.app.ui.common.rememberPermissionLauncher
import com.mosquetracker.app.ui.common.rememberPermissionsGranted

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenHome: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val mosques by viewModel.mosques.collectAsStateWithLifecycle()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val autoSilent by viewModel.autoSilent.collectAsStateWithLifecycle()
    val routineActions by viewModel.routineActions.collectAsStateWithLifecycle()
    val restorePending by viewModel.restorePending.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val searchMessage by viewModel.searchMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val foregroundPermissions = remember { RequiredPermissions.foreground() }
    val backgroundPermissions = remember { RequiredPermissions.backgroundLocation() }
    val foregroundGranted by rememberPermissionsGranted(foregroundPermissions)
    val backgroundGranted by rememberPermissionsGranted(backgroundPermissions)
    val launcher = rememberPermissionLauncher {
        viewModel.refreshOnline()
        viewModel.refreshAccess()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshOnline()
        viewModel.refreshAccess()
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    fun submitSearch() {
        viewModel.searchPlace(searchQuery, onFound = {
            searchQuery = ""
            onOpenHome()
        })
    }

    ScreenScaffold(title = "Settings & Mosques", modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!foregroundGranted) {
                PermissionBanner(
                    title = "Location permission required",
                    message = "MasjidSync needs GPS to show the map and to tell when you arrive.",
                    buttonText = "Grant",
                    onClick = { launcher.launch(foregroundPermissions.toTypedArray()) },
                )
            } else if (!backgroundGranted) {
                PermissionBanner(
                    title = "Allow background location",
                    message = "Choose \"Allow all the time\" so mosque detection keeps working when the app is closed.",
                    buttonText = "Allow",
                    onClick = { launcher.launch(backgroundPermissions.toTypedArray()) },
                )
            }

            Text("My Routine", style = MaterialTheme.typography.titleLarge)
            RoutineCard(
                mosques = mosques,
                monitoringEnabled = monitoringEnabled,
                routineEnabled = autoSilent,
                actions = routineActions,
                access = access,
                restorePending = restorePending,
                onToggle = viewModel::setRoutineAction,
                onRestoreNow = viewModel::restoreNow,
                onGrantDnd = { context.openNotificationPolicySettings() },
                onGrantWriteSettings = { context.openWriteSettings() },
                onSetSilent = { viewModel.setRoutineAction(RoutineAction.SILENT_RINGER, true) },
            )

            Text("Location & Geofencing", style = MaterialTheme.typography.titleLarge)
            SectionCard {
                if (mosques.isEmpty()) {
                    EmptyState(
                        icon = AppIcons.Place,
                        title = "No mosques yet",
                        message = "Add one below: search for it or use your current location.",
                    )
                } else {
                    mosques.forEach { mosque ->
                        TrackingRow(
                            mosque = mosque,
                            onToggle = { viewModel.setTracking(mosque, it) },
                            onModeToggle = { viewModel.toggleAlertMode(mosque) },
                            onDelete = { viewModel.deleteMosque(mosque) },
                        )
                    }
                }
            }

            Text("Add Mosque Options", style = MaterialTheme.typography.titleLarge)
            SectionCard {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.clearSearchMessage()
                    },
                    label = { Text("Search via Map") },
                    placeholder = { Text("Mosque or place name…") },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    },
                    singleLine = true,
                    enabled = online && !searching,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!online) {
                    Text(
                        text = "You're offline — search is disabled. Connect to search, or use your current location below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                searchMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        viewModel.useCurrentLocation()
                        onOpenHome()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = Color(0xFF3F2E00),
                    ),
                ) {
                    Icon(AppIcons.Send, contentDescription = null)
                    Text("  Use Current Location")
                }
            }

            Text("Monitoring", style = MaterialTheme.typography.titleLarge)
            SectionCard {
                SwitchRow(
                    title = "Background detection",
                    subtitle = "Foreground service + geofences for ${mosques.count { it.trackingEnabled }} tracked mosque(s)",
                    checked = monitoringEnabled,
                    onCheckedChange = viewModel::setMonitoring,
                )
                SwitchRow(
                    title = "Run the routine on arrival",
                    subtitle = "Applies the actions above; its sound actions also drive prayer silence",
                    checked = autoSilent,
                    onCheckedChange = viewModel::setAutoSilent,
                )
                if (!access.dnd) {
                    TextButton(onClick = { context.openNotificationPolicySettings() }) {
                        Text("Grant Do Not Disturb access")
                    }
                }
                if (!access.writeSettings) {
                    TextButton(onClick = { context.openWriteSettings() }) {
                        Text("Grant \"Modify system settings\"")
                    }
                }
            }

            Text("Not possible on Android", style = MaterialTheme.typography.titleLarge)
            BlockedActionsCard()
        }
    }
}

/**
 * The editable routine, in Samsung's If / Then / When-it-ends shape.
 *
 * "If" is read-only — it is simply the list of tracked mosques. Every row under "Then" and
 * "When it ends" is a switch the user owns, and each row says whether it can actually run
 * right now, so an action can never look armed while its access is missing.
 */
@Composable
private fun RoutineCard(
    mosques: List<MosqueEntity>,
    monitoringEnabled: Boolean,
    routineEnabled: Boolean,
    actions: Set<RoutineAction>,
    access: AccessState,
    restorePending: Boolean,
    onToggle: (RoutineAction, Boolean) -> Unit,
    onRestoreNow: () -> Unit,
    onGrantDnd: () -> Unit,
    onGrantWriteSettings: () -> Unit,
    onSetSilent: () -> Unit,
) {
    val tracked = mosques.filter { it.trackingEnabled }
    val armed = monitoringEnabled && routineEnabled
    val arrival = RoutineAction.onArrival()
    val exit = RoutineAction.onExit()
    val armedCount = arrival.count { it in actions }
    val dndMissing = !access.dnd &&
        arrival.any { it in actions && it.requirement == ActionRequirement.DND_ACCESS }
    val writeMissing = !access.writeSettings &&
        arrival.any { it in actions && it.requirement == ActionRequirement.WRITE_SETTINGS }
    // Muting the streams while leaving the ringer alone is the classic way to end up with a phone
    // that still rings inside the mosque: every volume row looks armed, but nothing silences calls.
    val ringerUntouched = RoutineAction.RINGER_ACTIONS.none { it in actions } &&
        arrival.any { it.group == ActionGroup.SOUND && it in actions }

    SectionCard {
        Text("If", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Arrival at any of these mosque zones",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!monitoringEnabled) {
            RoutineRow(
                icon = AppIcons.LocationOn,
                title = "Routine paused",
                subtitle = "Background detection is off",
                active = false,
            )
        } else if (!routineEnabled) {
            RoutineRow(
                icon = AppIcons.LocationOn,
                title = "Routine switched off",
                subtitle = "Turn on \"Run the routine on arrival\" below",
                active = false,
            )
        }
        if (tracked.isEmpty()) {
            RoutineRow(
                icon = AppIcons.LocationOn,
                title = "No tracked mosque",
                subtitle = "Add one below, or switch tracking back on",
                active = false,
            )
        } else {
            tracked.forEach { mosque ->
                RoutineRow(
                    icon = AppIcons.LocationOn,
                    title = mosque.name,
                    subtitle = "Inside the " +
                        "${DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius).toInt()} m zone" +
                        if (mosque.alertMode == AlertMode.NOTIFY_ONLY) " · notify only" else "",
                    active = true,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text("Then", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "$armedCount of ${arrival.size} actions armed · tap any row to change it",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RoutineAction.ON_ARRIVAL_GROUPS.forEach { group ->
            val rows = arrival.filter { it.group == group }
            if (rows.isNotEmpty()) {
                Text(
                    text = group.heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                rows.forEach { action ->
                    RoutineToggleRow(
                        action = action,
                        checked = action in actions,
                        armed = armed,
                        access = access,
                        coveredByRingMode = action == RoutineAction.MUTE_SYSTEM &&
                            RoutineAction.RINGER_ACTIONS.any { it in actions },
                        onToggle = { onToggle(action, it) },
                    )
                }
            }
        }
        if (dndMissing) {
            AccessHint(
                text = "Some armed actions need Do Not Disturb access.",
                buttonText = "Grant",
                onClick = onGrantDnd,
            )
        }
        if (writeMissing) {
            AccessHint(
                text = "Some armed actions need \"Modify system settings\".",
                buttonText = "Grant",
                onClick = onGrantWriteSettings,
            )
        }
        if (ringerUntouched) {
            AccessHint(
                text = "No ringer action is armed, so calls can still ring.",
                buttonText = "Set silent",
                onClick = onSetSilent,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text("When it ends", style = MaterialTheme.typography.titleMedium)
        exit.forEach { action ->
            RoutineToggleRow(
                action = action,
                checked = action in actions,
                armed = armed,
                access = access,
                coveredByRingMode = false,
                onToggle = { onToggle(action, it) },
            )
        }
        if (restorePending) {
            Text(
                text = "The phone is still in its arrival state.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRestoreNow) { Text("Restore now") }
        }
    }
}

/**
 * One editable action. The switch is always live — a user may arm an action before granting the
 * access it needs — but the subtitle says plainly when an armed action is not actually running.
 */
@Composable
private fun RoutineToggleRow(
    action: RoutineAction,
    checked: Boolean,
    armed: Boolean,
    access: AccessState,
    coveredByRingMode: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val granted = when (action.requirement) {
        ActionRequirement.NONE -> true
        ActionRequirement.DND_ACCESS -> access.dnd
        ActionRequirement.WRITE_SETTINGS -> access.writeSettings
    }
    val blocked = checked && !granted
    val paused = checked && granted && !armed

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(action.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = action.description + when {
                    blocked -> " — needs " + when (action.requirement) {
                        ActionRequirement.DND_ACCESS -> "Do Not Disturb access"
                        ActionRequirement.WRITE_SETTINGS -> "\"Modify system settings\""
                        ActionRequirement.NONE -> ""
                    }
                    coveredByRingMode -> " — already handled by the ring mode"
                    paused -> " — routine is off"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    blocked -> MaterialTheme.colorScheme.error
                    coveredByRingMode || paused -> MaterialTheme.colorScheme.onSurfaceVariant
                    checked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = MaterialTheme.colorScheme.secondary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun AccessHint(text: String, buttonText: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onClick) { Text(buttonText) }
    }
}

/** The honest list of phone switches Android keeps for itself. */
@Composable
private fun BlockedActionsCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }

    SectionCard {
        Text(
            text = "These exist on the phone but no app is allowed to flip them. " +
                "Tap to see the full list and the reason.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide the list" else "Show ${BLOCKED_ACTIONS.size} unavailable actions")
        }
        if (expanded) {
            BLOCKED_ACTIONS.forEach { blocked ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(blocked.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = blocked.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val RoutineAction.icon: ImageVector
    get() = when (this) {
        RoutineAction.SILENT_RINGER,
        RoutineAction.DND_TOTAL_SILENCE,
        RoutineAction.DND_ALARMS_ONLY,
        -> AppIcons.Silent

        RoutineAction.VIBRATE_RINGER,
        RoutineAction.VIBRATE_PULSE,
        -> AppIcons.Vibration

        RoutineAction.MUTE_MEDIA,
        RoutineAction.MUTE_ALARM,
        RoutineAction.MUTE_NOTIFICATION,
        RoutineAction.MUTE_SYSTEM,
        RoutineAction.RESTORE_SOUND,
        -> AppIcons.VolumeOff

        RoutineAction.FLASHLIGHT_ON -> AppIcons.Lightbulb
        RoutineAction.MIN_BRIGHTNESS -> AppIcons.BrightnessLow
        RoutineAction.SHORT_SCREEN_TIMEOUT -> AppIcons.Timer
        RoutineAction.LOCK_ROTATION,
        RoutineAction.RESTORE_DEVICE,
        -> AppIcons.ScreenRotation

        RoutineAction.ARRIVAL_NOTICE,
        RoutineAction.EXIT_NOTICE,
        -> AppIcons.Notifications
    }

@Composable
private fun RoutineRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** One mosque with its tracking switch. Off renders gold, echoing the design mock. */@Composable
private fun TrackingRow(
    mosque: MosqueEntity,
    onToggle: (Boolean) -> Unit,
    onModeToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mosque.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Enable/disable tracking  ·  " +
                        "${DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius).toInt()} m zone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(AppIcons.Delete, contentDescription = "Delete ${mosque.name}")
            }
            Switch(
                checked = mosque.trackingEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    uncheckedTrackColor = MaterialTheme.colorScheme.secondary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        TextButton(onClick = onModeToggle, modifier = Modifier.align(Alignment.Start)) {
            Text(
                text = if (mosque.alertMode == AlertMode.FULL_SILENT) {
                    "Arrival: full silent — tap for notify only"
                } else {
                    "Arrival: notify only — tap for full silent"
                },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = MaterialTheme.colorScheme.secondary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(buttonText) }
        }
    }
}

private fun Context.openNotificationPolicySettings() {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/** Opens the per-app "Modify system settings" screen, the only way to earn WRITE_SETTINGS. */
private fun Context.openWriteSettings() {
    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        .setData("package:$packageName".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
