package com.example.mockgps.ui.map

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.example.mockgps.domain.SimulationState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onNavigateToSavedLocations: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.centerLocation, 15f)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
             viewModel.onCameraMove(cameraPositionState.position.target)
        }
    }

    // Initial check on cold start
    LaunchedEffect(Unit) {
        viewModel.refreshMockPermission()
    }

    // Check on resume (e.g. returning from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMockPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.centerLocation) {
        if (cameraPositionState.position.target != uiState.centerLocation) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(uiState.centerLocation, 15f)
        }
    }

    Scaffold(
        topBar = {
            // Overlay Top Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
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
                    IconButton(onClick = onNavigateToSavedLocations) {
                        Icon(Icons.Default.List, contentDescription = "Saved Locations")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                uiState.savedLocations.forEach { location ->
                    Marker(
                        state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                        title = location.name,
                        snippet = "Lat: ${location.latitude}, Lng: ${location.longitude}",
                        onClick = {
                            val target = LatLng(location.latitude, location.longitude)
                            viewModel.onCameraMove(target)
                            // cameraPositionState update handled by LaunchedEffect
                            false
                        },
                        icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE)
                    )
                }

                uiState.waypoints.forEachIndexed { index, point ->
                    Marker(
                        state = MarkerState(position = point),
                        title = "Point ${index + 1}",
                        icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_CYAN)
                    )
                }

                if (uiState.waypoints.size > 1) {
                    Polyline(
                        points = uiState.waypoints,
                        color = Color.Blue,
                        width = 10f
                    )
                }

                val mockLoc = uiState.currentLocation ?: if (uiState.isMocking && uiState.simulationState == SimulationState.IDLE) uiState.centerLocation else null

                if (mockLoc != null && uiState.isMocking) {
                     Marker(
                        state = MarkerState(position = mockLoc),
                        title = "Mocking Here",
                        icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
            }

            // Center Crosshair
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Center",
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                tint = Color.Red
            )

            // Bottom Controls
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
        }
    }

    if (uiState.mockError != null) {
        val error = uiState.mockError!!
        var isButtonEnabled by remember { mutableStateOf(false) }

        // Anti-misclick delay: button enables after 500ms
        LaunchedEffect(error) {
            isButtonEnabled = false
            delay(500)
            isButtonEnabled = true
        }

        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(if (error is MockError.NotMockAppSelected) "Permission Required" else "Error") },
            text = {
                Column {
                    Text(text = when (error) {
                        is MockError.NotMockAppSelected -> "Please go to Developer Options -> Select mock location app -> Select this app."
                        is MockError.ProviderSetupFailed -> "Mock Engine Setup Failed: ${error.message}"
                        is MockError.SetLocationFailed -> "Set Location Failed: ${error.message}"
                        is MockError.ProviderTeardownFailed -> "Teardown Failed: ${error.message}"
                        is MockError.InvalidInput -> "Invalid Input: ${error.message}"
                        is MockError.PermissionCheckFailed -> "Permission Check Failed: ${error.message}"
                        is MockError.Unknown -> "Unknown Error: ${error.message}"
                    })
                }
            },
            confirmButton = {
                if (error is MockError.NotMockAppSelected) {
                    Button(
                        onClick = {
                            viewModel.clearError()
                            try {
                                val appDevIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                context.startActivity(appDevIntent)
                            } catch (e: Exception) {
                                try {
                                    val devIntent = Intent("android.settings.DEVELOPMENT_SETTINGS")
                                    context.startActivity(devIntent)
                                } catch (e2: Exception) {
                                    try {
                                        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(settingsIntent)
                                    } catch (e3: Exception) {
                                        // Ignore or show toast
                                    }
                                }
                            }
                        },
                        enabled = isButtonEnabled
                    ) {
                        Text(if (isButtonEnabled) "Go to Settings" else "Wait...")
                    }
                } else {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (error is MockError.NotMockAppSelected) {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
