package com.mosquetracker.app.ui.addmosque

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * "Add current location as mosque" dialog. Captures the live GPS fix and the
 * connected Wi-Fi BSSID, then writes a local mosque and arms its geofence.
 */
@Composable
fun AddMosqueDialog(
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: AddMosqueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(100f) }
    var bindWifi by remember { mutableStateOf(true) }

    val canSave = name.isNotBlank() && state.latitude != null && state.longitude != null && !state.saving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add current location as mosque") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mosque name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val latitude = state.latitude
                val longitude = state.longitude
                if (state.loading) {
                    Text("Reading GPS…", style = MaterialTheme.typography.bodyMedium)
                } else if (latitude == null || longitude == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "No GPS fix yet. Enable location and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.capture() }) { Text("Retry") }
                    }
                } else {
                    Text(
                        text = "Lat ${"%.5f".format(latitude)}  ·  Lon ${"%.5f".format(longitude)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "GPS accuracy ±${"%.0f".format(state.accuracyMeters ?: 0f)} m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Geofence radius: ${radius.roundToInt()} m",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 50f..500f,
                    steps = 8,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bind connected Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = state.connectedBssid ?: "No Wi-Fi connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = bindWifi && state.connectedBssid != null,
                        onCheckedChange = { bindWifi = it },
                        enabled = state.connectedBssid != null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    viewModel.save(
                        name = name,
                        radiusMeters = radius.roundToInt(),
                        bindCurrentWifi = bindWifi,
                        onSaved = onSaved,
                    )
                },
            ) { Text(if (state.saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
