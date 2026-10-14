package com.mosquetracker.app.ui.mosques

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.R
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.model.BleBeacon
import com.mosquetracker.app.ui.addmosque.AddMosqueDialog
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.EmptyState
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard
import kotlinx.coroutines.delay

@Composable
fun MosquesScreen(
    modifier: Modifier = Modifier,
    viewModel: MosquesViewModel = hiltViewModel(),
) {
    val mosques by viewModel.mosques.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val beacons by viewModel.discoveredBeacons.collectAsStateWithLifecycle()
    val scanningBeacons by viewModel.scanningBeacons.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var beaconTarget by remember { mutableStateOf<MosqueEntity?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            viewModel.clearMessage()
        }
    }

    ScreenScaffold(
        title = "Detection Override Center",
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text("Add mosque") },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            message?.let { text ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(text, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (mosques.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Add,
                        title = "No mosques yet",
                        message = "Tap “Add mosque” while standing inside a mosque to save it as a geofence.",
                    )
                }
            }

            items(mosques, key = { it.id }) { mosque ->
                MosqueCard(
                    mosque = mosque,
                    onBindWifi = { viewModel.bindConnectedWifi(mosque) },
                    onUnbindWifi = { viewModel.unbindWifi(mosque) },
                    onBindBeacon = { beaconTarget = mosque },
                    onUnbindBeacon = { viewModel.unbindBeacon(mosque) },
                    onDelete = { viewModel.delete(mosque) },
                )
            }
        }
    }

    if (showAddDialog) {
        AddMosqueDialog(
            onDismiss = { showAddDialog = false },
            onSaved = { showAddDialog = false },
        )
    }

    beaconTarget?.let { mosque ->
        BeaconPickerDialog(
            beacons = beacons,
            scanning = scanningBeacons,
            onScan = { viewModel.scanForBeacons() },
            onPick = { beacon ->
                viewModel.bindBeacon(mosque, beacon)
                beaconTarget = null
            },
            onDismiss = { beaconTarget = null },
        )
    }
}

@Composable
private fun MosqueCard(
    mosque: MosqueEntity,
    onBindWifi: () -> Unit,
    onUnbindWifi: () -> Unit,
    onBindBeacon: () -> Unit,
    onUnbindBeacon: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mosque.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${"%.5f".format(mosque.latitude)}, ${"%.5f".format(mosque.longitude)}  ·  ${mosque.geofenceRadius} m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(AppIcons.Delete, contentDescription = "Delete ${mosque.name}")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = mosque.wifiBssid ?: "no Wi-Fi bound",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (mosque.wifiBssid == null) {
                    AssistChip(onClick = onBindWifi, label = { Text("Bind current") })
                } else {
                    AssistChip(onClick = onUnbindWifi, label = { Text("Unbind") })
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = mosque.bleUuid ?: "no beacon bound",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (mosque.bleUuid == null) {
                    AssistChip(onClick = onBindBeacon, label = { Text("Bind beacon") })
                } else {
                    AssistChip(onClick = onUnbindBeacon, label = { Text("Unbind") })
                }
            }
        }
    }
}

@Composable
private fun BeaconPickerDialog(
    beacons: List<BleBeacon>,
    scanning: Boolean,
    onScan: () -> Unit,
    onPick: (BleBeacon) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bind a Bluetooth beacon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (scanning) "Scanning…" else "Pick the beacon to bind to this mosque.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (beacons.isEmpty()) {
                    Text(
                        text = "No beacons seen yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(beacons, key = { it.address }) { beacon ->
                            SectionCard {
                                Text(
                                    text = beacon.name ?: "(unnamed device)",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${beacon.address}  ·  ${beacon.rssi} dBm",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { onPick(beacon) }) { Text("Bind this beacon") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onScan, enabled = !scanning) {
                Text(if (scanning) "Scanning…" else "Scan again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
