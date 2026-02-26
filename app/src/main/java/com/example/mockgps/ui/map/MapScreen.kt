package com.example.mockgps.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mockgps.domain.SimulationState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.centerLocation, 15f)
    }

    // Sync camera movement to ViewModel
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
             viewModel.onCameraMove(cameraPositionState.position.target)
        }
    }

    // Check permission on start
    LaunchedEffect(Unit) {
        viewModel.checkMockPermission()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            // Saved locations markers
            uiState.savedLocations.forEach { location ->
                Marker(
                    state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                    title = location.name,
                    snippet = "Lat: ${location.latitude}, Lng: ${location.longitude}",
                    onClick = {
                        val target = LatLng(location.latitude, location.longitude)
                        viewModel.onCameraMove(target)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 15f)
                        false // Return false to allow default behavior (showing info window)
                    },
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE)
                )
            }

            // Route Waypoints
            uiState.waypoints.forEachIndexed { index, point ->
                Marker(
                    state = MarkerState(position = point),
                    title = "Point ${index + 1}",
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_CYAN)
                )
            }

            // Route Polyline
            if (uiState.waypoints.size > 1) {
                Polyline(
                    points = uiState.waypoints,
                    color = Color.Blue,
                    width = 10f
                )
            }

            // Current Mock Location Marker (if mocking)
            // If simulating, show the dynamic location. If static mocking, show center.
            val mockLoc = uiState.currentLocation ?: if (uiState.isMocking && uiState.simulationState == SimulationState.IDLE) uiState.centerLocation else null

            if (mockLoc != null && uiState.isMocking) {
                 Marker(
                    state = MarkerState(position = mockLoc),
                    title = "Mocking Here",
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE)
                )
            }
        }

        // Center Crosshair (Only visible if not simulating or strictly following?)
        // Always visible to allow adding points
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Center",
            modifier = Modifier.align(Alignment.Center).size(32.dp),
            tint = Color.Red
        )

        // Top Info Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Lat: %.6f".format(uiState.centerLocation.latitude))
                Text(text = "Lng: %.6f".format(uiState.centerLocation.longitude))

                val statusText = when {
                    uiState.simulationState == SimulationState.PLAYING -> "SIMULATING (${"%.1f".format(uiState.speedKmh)} km/h)"
                    uiState.isMocking -> "MOCKING (Fixed)"
                    else -> "IDLE"
                }
                val statusColor = if (uiState.isMocking || uiState.simulationState == SimulationState.PLAYING) Color.Green else Color.Gray
                Text(text = "Status: $statusText", color = statusColor)
            }
        }

        // Bottom Controls (Route & Mock)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Route Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { viewModel.addWaypoint() }) {
                        Text("Add Point")
                    }
                    Button(onClick = { viewModel.clearRoute() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Transport Mode & Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TransportMode.values().forEach { mode ->
                         FilterChip(
                            selected = uiState.transportMode == mode,
                            onClick = { viewModel.setTransportMode(mode) },
                            label = { Text(mode.name.take(1)) }
                        )
                    }
                }

                Slider(
                    value = uiState.speedKmh.toFloat(),
                    onValueChange = { viewModel.setSpeed(it.toDouble()) },
                    valueRange = 0f..100f,
                    steps = 19
                )
                Text("Speed: ${"%.1f".format(uiState.speedKmh)} km/h", modifier = Modifier.align(Alignment.CenterHorizontally))

                Spacer(modifier = Modifier.height(8.dp))

                // Play/Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            when {
                                uiState.simulationState == SimulationState.PLAYING -> viewModel.pauseRoute()
                                uiState.waypoints.isNotEmpty() -> viewModel.playRoute()
                                uiState.isMocking -> viewModel.stopMocking()
                                else -> viewModel.startMocking()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isMocking) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = when {
                                uiState.simulationState == SimulationState.PLAYING -> "Pause Route"
                                uiState.waypoints.isNotEmpty() -> "Play Route"
                                uiState.isMocking -> "Stop Mocking"
                                else -> "Start Mocking"
                            }
                        )
                    }
                }
            }
        }

        // Error Dialog
        if (uiState.mockError != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Error") },
                text = { Text(uiState.mockError ?: "") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
