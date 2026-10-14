package com.mosquetracker.app.ui.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.R
import com.mosquetracker.app.model.BleBeacon
import com.mosquetracker.app.model.WifiAccessPoint
import com.mosquetracker.app.ui.common.KeyValueRow
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.SectionCard

@Composable
fun SensorsScreen(
    modifier: Modifier = Modifier,
    viewModel: SensorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Sensors", modifier = modifier) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                SectionCard(title = "Location") {
                    val location = state.location
                    KeyValueRow(
                        label = "Latitude",
                        value = location?.let { "%.6f".format(it.latitude) } ?: "waiting for fix…",
                    )
                    KeyValueRow(
                        label = "Longitude",
                        value = location?.let { "%.6f".format(it.longitude) } ?: "waiting for fix…",
                    )
                    KeyValueRow(
                        label = "Accuracy",
                        value = location?.let { "±${"%.0f".format(it.accuracyMeters)} m" } ?: "—",
                    )
                }
            }

            item {
                SectionCard(title = "Wi-Fi networks") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = wifiStatus(state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        )
                        OutlinedButton(
                            onClick = { viewModel.scanWifi() },
                            enabled = !state.scanningWifi,
                        ) {
                            Text(if (state.scanningWifi) "Scanning…" else "Scan")
                        }
                    }

                    if (state.wifiAccessPoints.isEmpty() && !state.scanningWifi) {
                        Text(
                            text = "No access points seen yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.wifiAccessPoints.forEach { ap ->
                        HorizontalDivider()
                        WifiRow(ap)
                    }
                }
            }

            item {
                SectionCard(title = "Bluetooth beacons") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bluetooth),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = bleStatus(state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        )
                        OutlinedButton(onClick = { viewModel.toggleBleScan() }) {
                            Text(if (state.scanningBle) "Stop" else "Scan")
                        }
                    }

                    if (state.beacons.isEmpty() && !state.scanningBle) {
                        Text(
                            text = "No beacons seen yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.beacons.forEach { beacon ->
                        HorizontalDivider()
                        BeaconRow(beacon)
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiRow(ap: WifiAccessPoint) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = ap.ssid ?: "<hidden SSID>",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${ap.bssid}  ·  ${ap.rssi} dBm  ·  ${ap.frequencyMhz} MHz",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BeaconRow(beacon: BleBeacon) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = beacon.name ?: "(unnamed device)",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(beacon.address)
                append("  ·  ${beacon.rssi} dBm")
                if (beacon.serviceUuids.isNotEmpty()) {
                    append("  ·  ${beacon.serviceUuids.first()}")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun wifiStatus(state: SensorsViewModel.UiState): String = when {
    !state.wifiSupported -> "Wi-Fi not supported on this device"
    !state.wifiPermission -> "Wi-Fi permission not granted"
    !state.wifiEnabled -> "Wi-Fi is turned off"
    state.scanningWifi -> "Scanning…"
    else -> "${state.wifiAccessPoints.size} network(s) nearby"
}

private fun bleStatus(state: SensorsViewModel.UiState): String = when {
    !state.bleSupported -> "Bluetooth not supported on this device"
    !state.blePermission -> "Bluetooth permission not granted"
    !state.bleEnabled -> "Bluetooth is turned off"
    state.scanningBle -> "Scanning…"
    else -> "${state.beacons.size} beacon(s) seen"
}
