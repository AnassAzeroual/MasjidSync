package com.mosquetracker.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.model.ActionGroup
import com.mosquetracker.app.model.ActionRequirement
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.PermissionBanners
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.openNotificationPolicySettings
import com.mosquetracker.app.ui.common.openWriteSettings
import com.mosquetracker.app.ui.common.rememberPermissionStatus

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val mosques by viewModel.mosques.collectAsStateWithLifecycle()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val autoSilent by viewModel.autoSilent.collectAsStateWithLifecycle()
    val actions by viewModel.routineActions.collectAsStateWithLifecycle()
    val restorePending by viewModel.restorePending.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val fenceState by viewModel.fenceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberPermissionStatus(onResult = viewModel::onPermissionsChanged)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingMosqueId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingMosqueId by rememberSaveable { mutableStateOf<Long?>(null) }
    val trackedIds = mosques.filter { it.trackingEnabled }.mapTo(mutableSetOf()) { it.id }
    val detection = detectionSummary(monitoringEnabled, fenceState, trackedIds)
    val arrival = RoutineAction.onArrival()
    val missingDnd = !access.dnd && actions.any { it.requirement == ActionRequirement.DND_ACCESS }
    val missingWrite = !access.writeSettings && actions.any { it.requirement == ActionRequirement.WRITE_SETTINGS }
    val ringerUntouched = RoutineAction.RINGER_ACTIONS.none { it in actions } &&
        arrival.any { it.group == ActionGroup.SOUND && it in actions }

    ScreenScaffold(
        title = "Settings",
        modifier = modifier,
        headerContainerColor = MaterialTheme.colorScheme.surface,
        headerContentColor = MaterialTheme.colorScheme.onSurface,
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    SettingsHeading("Your routine, your way", "A quieter phone. More time to be present.")
                }
                item(key = "overview") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Silent, contentDescription = null, modifier = Modifier.size(28.dp))
                                Text("Made for your mosque visits", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                "${trackedIds.size} tracked ${if (trackedIds.size == 1) "mosque" else "mosques"}  ·  " +
                                    "${arrival.count { it in actions }} arrival actions selected",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                            Text(
                                if (monitoringEnabled) detection.text
                                else "Detection is paused. Use the MasjidSync Quick Settings tile to resume.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!autoSilent) {
                                Text("Your arrival routine is paused.", style = MaterialTheme.typography.bodyMedium)
                                TextButton(
                                    onClick = { viewModel.setAutoSilent(true) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                ) { Text("Resume routine") }
                            }
                        }
                    }
                }
                if (permissions.missing.isNotEmpty()) {
                    item(key = "permissions") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { PermissionBanners(permissions) }
                    }
                }
                if (restorePending) {
                    item(key = "restore") {
                        AccessHint("Your phone still has changes from a visit.", "Restore now", viewModel::restoreNow)
                    }
                }
                if (missingDnd) {
                    item(key = "dnd") {
                        AccessHint("Selected sound actions need Do Not Disturb access.", "Allow access") {
                            context.openNotificationPolicySettings()
                        }
                    }
                }
                if (missingWrite) {
                    item(key = "write-access") {
                        AccessHint("Selected device actions need permission to modify system settings.", "Allow changes") {
                            context.openWriteSettings()
                        }
                    }
                }
                if (ringerUntouched) {
                    item(key = "ringer") {
                        AccessHint("Calls can still ring: no ringer action is selected.", "Set silent") {
                            viewModel.setRoutineAction(RoutineAction.SILENT_RINGER, true)
                        }
                    }
                }
                item(key = "arrival-heading") {
                    SettingsHeading("On arrival", "Choose what changes when you enter a mosque.")
                }
                items(RoutineAction.ON_ARRIVAL_GROUPS, key = { "group-$it" }) { group ->
                    val groupActions = arrival.filter { it.group == group }
                    ActionGroupCard(
                        title = group.title,
                        icon = group.icon,
                        groupActions = groupActions,
                        selected = actions,
                        onClick = { selectedGroup = group.name },
                    )
                }
                item(key = "departure-heading") {
                    SettingsHeading("When you leave", "Return to your day, just as it was.")
                }
                item(key = "departure") {
                    ActionGroupCard(
                        title = "Restore & notify",
                        icon = AppIcons.Refresh,
                        groupActions = RoutineAction.onExit(),
                        selected = actions,
                        onClick = { selectedGroup = "EXIT" },
                    )
                }
                item(key = "mosques-heading") {
                    SettingsHeading("Your mosques", "Names, arrival behaviour and individual tracking.")
                }
                if (mosques.isEmpty()) {
                    item(key = "empty") {
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            EmptyState(
                                icon = AppIcons.Place,
                                title = "No saved mosques yet",
                                message = "Save a mosque using the + button on Home. Manage it here afterwards.",
                            )
                        }
                    }
                }
                items(mosques, key = { "mosque-${it.id}" }) { mosque ->
                    TrackingRow(
                        mosque = mosque,
                        onToggle = { viewModel.setTracking(mosque, it) },
                        onModeToggle = { viewModel.toggleAlertMode(mosque) },
                        onRename = { renamingMosqueId = mosque.id },
                        onDelete = { deletingMosqueId = mosque.id },
                    )
                }
            }
        }
    }

    selectedGroup?.let { key ->
        val group = ActionGroup.entries.find { it.name == key }
        RoutineSheet(
            title = group?.title ?: "Restore & notify",
            actions = if (group == null) RoutineAction.onExit() else arrival.filter { it.group == group },
            selected = actions,
            armed = monitoringEnabled && autoSilent,
            access = access,
            onToggle = viewModel::setRoutineAction,
            onGrantDnd = { context.openNotificationPolicySettings() },
            onGrantWrite = { context.openWriteSettings() },
            onDismiss = { selectedGroup = null },
        )
    }
    mosques.firstOrNull { it.id == renamingMosqueId }?.let { mosque ->
        RenameMosqueDialog(mosque, onDismiss = { renamingMosqueId = null }) { name ->
            viewModel.renameMosque(mosque, name)
            renamingMosqueId = null
        }
    }
    mosques.firstOrNull { it.id == deletingMosqueId }?.let { mosque ->
        AlertDialog(
            onDismissRequest = { deletingMosqueId = null },
            title = { Text("Delete mosque?") },
            text = { Text("${mosque.name} and all its visit history will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMosque(mosque)
                        deletingMosqueId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingMosqueId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsHeading(title: String, detail: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionGroupCard(
    title: String,
    icon: ImageVector,
    groupActions: List<RoutineAction>,
    selected: Set<RoutineAction>,
    onClick: () -> Unit,
) {
    val enabled = groupActions.filter { it in selected }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "$title, ${enabled.size} of ${groupActions.size} selected. Edit actions."
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${enabled.size} of ${groupActions.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineSheet(
    title: String,
    actions: List<RoutineAction>,
    selected: Set<RoutineAction>,
    armed: Boolean,
    access: AccessState,
    onToggle: (RoutineAction, Boolean) -> Unit,
    onGrantDnd: () -> Unit,
    onGrantWrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Edit actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(AppIcons.Close, contentDescription = "Close actions") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") { SettingsHeading(title, "Changes are saved automatically.") }
            items(actions, key = { it.name }) { action ->
                RoutineToggleRow(
                    action = action,
                    checked = action in selected,
                    armed = armed,
                    access = access,
                    coveredByRingMode = action == RoutineAction.MUTE_SYSTEM && RoutineAction.RINGER_ACTIONS.any { it in selected },
                    onToggle = { onToggle(action, it) },
                )
            }
            if (!access.dnd && actions.any { it in selected && it.requirement == ActionRequirement.DND_ACCESS }) {
                item(key = "dnd") { AccessHint("Android needs Do Not Disturb access for these actions.", "Allow access", onGrantDnd) }
            }
            if (!access.writeSettings && actions.any { it in selected && it.requirement == ActionRequirement.WRITE_SETTINGS }) {
                item(key = "write") { AccessHint("Android needs permission to modify system settings.", "Allow changes", onGrantWrite) }
            }
        }
    }
}

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
    val status = when {
        checked && !granted -> when (action.requirement) {
            ActionRequirement.DND_ACCESS -> "Needs Do Not Disturb access"
            ActionRequirement.WRITE_SETTINGS -> "Needs Modify system settings access"
            ActionRequirement.NONE -> ""
        }
        coveredByRingMode -> "Already handled by the ring mode"
        checked && !armed -> "Selected · routine paused"
        checked -> "Selected"
        else -> null
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onToggle).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(action.icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(action.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = checked, onCheckedChange = null)
            }
            Text(action.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (checked && !granted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccessHint(text: String, buttonText: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            TextButton(
                onClick = onClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
            ) { Text(buttonText) }
        }
    }
}

@Composable
private fun TrackingRow(
    mosque: MosqueEntity,
    onToggle: (Boolean) -> Unit,
    onModeToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Place, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mosque.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Mosque #${mosque.id} · ${DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius).toInt()} m zone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Rename ${mosque.name}" },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("Rename") }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Delete ${mosque.name}" },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .semantics { contentDescription = "Track ${mosque.name}" }
                    .toggleable(mosque.trackingEnabled, role = Role.Switch, onValueChange = onToggle),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Track this mosque", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = mosque.trackingEnabled, onCheckedChange = null)
            }
            Text("On arrival", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                listOf(AlertMode.FULL_SILENT, AlertMode.NOTIFY_ONLY).forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mosque.alertMode == mode,
                        onClick = { if (mosque.alertMode != mode) onModeToggle() },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.fillMaxHeight().heightIn(min = 48.dp),
                    ) { Text(if (mode == AlertMode.FULL_SILENT) "Full routine" else "Notify only") }
                }
            }
        }
    }
}

@Composable
private fun RenameMosqueDialog(mosque: MosqueEntity, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable(mosque.id) { mutableStateOf(mosque.name) }
    val focusManager = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename mosque") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Mosque name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val ActionGroup.title: String
    get() = when (this) {
        ActionGroup.SOUND -> "Sound & vibration"
        ActionGroup.FOCUS -> "Do Not Disturb"
        ActionGroup.DEVICE -> "Screen & device"
        ActionGroup.NOTICE -> "Arrival notification"
    }

private val ActionGroup.icon: ImageVector
    get() = when (this) {
        ActionGroup.SOUND -> AppIcons.VolumeOff
        ActionGroup.FOCUS -> AppIcons.Silent
        ActionGroup.DEVICE -> AppIcons.BrightnessLow
        ActionGroup.NOTICE -> AppIcons.Notifications
    }

private val RoutineAction.icon: ImageVector
    get() = when (this) {
        RoutineAction.SILENT_RINGER, RoutineAction.DND_TOTAL_SILENCE, RoutineAction.DND_ALARMS_ONLY -> AppIcons.Silent
        RoutineAction.VIBRATE_RINGER, RoutineAction.VIBRATE_PULSE -> AppIcons.Vibration
        RoutineAction.MUTE_MEDIA, RoutineAction.MUTE_ALARM, RoutineAction.MUTE_NOTIFICATION,
        RoutineAction.MUTE_SYSTEM, RoutineAction.RESTORE_SOUND -> AppIcons.VolumeOff
        RoutineAction.FLASHLIGHT_ON -> AppIcons.Lightbulb
        RoutineAction.MIN_BRIGHTNESS -> AppIcons.BrightnessLow
        RoutineAction.SHORT_SCREEN_TIMEOUT -> AppIcons.Timer
        RoutineAction.LOCK_ROTATION, RoutineAction.RESTORE_DEVICE -> AppIcons.ScreenRotation
        RoutineAction.ARRIVAL_NOTICE, RoutineAction.EXIT_NOTICE -> AppIcons.Notifications
    }
