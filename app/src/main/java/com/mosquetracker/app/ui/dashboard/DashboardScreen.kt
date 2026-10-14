package com.mosquetracker.app.ui.dashboard

import android.app.NotificationManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.R
import com.mosquetracker.app.model.DetectionOutcome
import com.mosquetracker.app.ui.addmosque.AddMosqueDialog
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.KeyValueRow
import com.mosquetracker.app.ui.common.RequiredPermissions
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import com.mosquetracker.app.ui.common.rememberPermissionLauncher
import com.mosquetracker.app.ui.common.rememberPermissionsGranted
import com.mosquetracker.app.util.displayName

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    val foregroundPermissions = remember { RequiredPermissions.foreground() }
    val backgroundPermissions = remember { RequiredPermissions.backgroundLocation() }
    val foregroundGranted by rememberPermissionsGranted(foregroundPermissions)
    val backgroundGranted by rememberPermissionsGranted(backgroundPermissions)
    val launcher = rememberPermissionLauncher { viewModel.refreshHardware() }

    val ringerAccessGranted = rememberPolicyAccessGranted()

    ScreenScaffold(
        title = "Mosque Tracker",
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text("Add mosque") },
            )
        },
    ) { contentPadding ->
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
                    message = "Mosque Tracker needs location (and Wi-Fi/Bluetooth on newer Android) to detect when you arrive.",
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

            StatusCard(state)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(AppIcons.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Add current location as mosque")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.verifyNow() },
                            enabled = state.nearestMosque != null && !state.verifying,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(if (state.verifying) "  Verifying…" else "  Verify now")
                        }
                        OutlinedButton(
                            onClick = { viewModel.logManualVisit() },
                            enabled = state.nearestMosque != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(AppIcons.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Log visit")
                        }
                    }
                }
            }

            SectionCard(title = "Monitoring") {
                SwitchRow(
                    title = "Background detection",
                    subtitle = "Foreground service + geofences for ${state.mosqueCount} saved mosque(s)",
                    checked = state.monitoringEnabled,
                    onCheckedChange = viewModel::setMonitoring,
                )
                SwitchRow(
                    title = "Silence phone on arrival",
                    subtitle = if (ringerAccessGranted) {
                        "Restores your ringer when you leave"
                    } else {
                        "Needs Do Not Disturb access"
                    },
                    checked = state.autoSilent,
                    onCheckedChange = viewModel::setAutoSilent,
                )
                if (!ringerAccessGranted) {
                    TextButton(onClick = { context.openNotificationPolicySettings() }) {
                        Text("Open Do Not Disturb access settings")
                    }
                }
            }

            SectionCard(title = "Detected hardware") {
                KeyValueRow(
                    label = "GPS accuracy",
                    value = state.currentLocation?.let { "±${"%.0f".format(it.accuracyMeters)} m" } ?: "—",
                )
                KeyValueRow(
                    label = "Coordinates",
                    value = state.currentLocation?.let {
                        "${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}"
                    } ?: "Waiting for fix…",
                )
                KeyValueRow(
                    label = "Connected Wi-Fi BSSID",
                    value = state.connectedBssid ?: "not connected",
                    leadingPainter = painterResource(R.drawable.ic_wifi),
                )
            }

            SectionCard(title = "Summary") {
                KeyValueRow(label = "Saved mosques", value = state.mosqueCount.toString())
                KeyValueRow(label = "Visits today", value = state.todayVisits.toString())
            }

            state.lastOutcome?.let { OutcomeCard(it) }
        }
    }

    if (showAddDialog) {
        AddMosqueDialog(
            onDismiss = { showAddDialog = false },
            onSaved = { showAddDialog = false },
        )
    }
}

@Composable
private fun StatusCard(state: DashboardUiState) {
    val inside = state.isInside
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (inside) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (inside) "INSIDE" else "OUTSIDE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.nearestMosque?.name ?: "No mosque detected",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = when {
                    state.nearestMosque == null -> "Add a mosque to start monitoring."
                    state.nearestDistanceMeters != null ->
                        "Nearest saved mosque is ${state.nearestDistanceMeters.toInt()} m away."
                    else -> "Waiting for a location fix…"
                },
                style = MaterialTheme.typography.bodyMedium,
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OutcomeCard(outcome: DetectionOutcome) {
    SectionCard(title = if (outcome.confirmed) "Verified" else "Not verified") {
        Text(outcome.explanation, style = MaterialTheme.typography.bodyMedium)
        KeyValueRow(label = "Method", value = outcome.method.displayName())
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

@Composable
private fun rememberPolicyAccessGranted(): Boolean {
    val context = LocalContext.current
    fun isGranted(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return manager?.isNotificationPolicyAccessGranted == true
    }
    val granted = remember { mutableStateOf(isGranted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted.value = isGranted() }
    return granted.value
}

private fun Context.openNotificationPolicySettings() {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
