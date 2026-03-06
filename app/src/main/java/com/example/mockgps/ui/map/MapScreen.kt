package com.example.mockgps.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mockgps.utils.LocationQueryParser
import com.example.mockgps.utils.ParseResult
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.utils.LatLngBoundsUtil
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val DEFAULT_ROUTE_FALLBACK_ZOOM = 15f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    selectedLocation: LatLng? = null,
    onSelectedLocationConsumed: () -> Unit = {},
    onNavigateToSavedLocations: () -> Unit = {},
    onNavigateToRoutes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var showSearchDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }
    val mockMarkerState = remember { MarkerState(position = uiState.centerLocation) }
    var mapContainerSize by remember { mutableStateOf(IntSize.Zero) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.centerLocation, 15f)
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            viewModel.onCameraMove(cameraPositionState.position.target)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshMockPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshMockPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.centerLocation) {
        if (cameraPositionState.position.target != uiState.centerLocation) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(uiState.centerLocation, 15f)
        }
    }

    LaunchedEffect(selectedLocation, uiState.routeFitRequestToken) {
        // Route fit has higher priority than saved-location recenter when both exist.
        if (selectedLocation != null && uiState.routeFitRequestToken == null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f))
            viewModel.onCameraMove(selectedLocation)
            onSelectedLocationConsumed()
        }
    }

    LaunchedEffect(uiState.routeFitRequestToken, mapContainerSize) {
        if (uiState.routeFitRequestToken == null) {
            return@LaunchedEffect
        }

        val fitTarget = LatLngBoundsUtil.calculateFitTarget(uiState.waypoints)

        when {
            fitTarget.bounds != null -> {
                val boundsCenter = LatLng(
                    (fitTarget.bounds.minLat + fitTarget.bounds.maxLat) / 2.0,
                    (fitTarget.bounds.minLng + fitTarget.bounds.maxLng) / 2.0
                )
                val widthPx = mapContainerSize.width
                val heightPx = mapContainerSize.height
                if (widthPx <= 2 || heightPx <= 2) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(boundsCenter, DEFAULT_ROUTE_FALLBACK_ZOOM)
                    )
                    viewModel.onCameraMove(boundsCenter)
                    viewModel.onRouteFitConsumed()
                    return@LaunchedEffect
                }

                val mapPaddingPx = with(density) { 80.dp.roundToPx() }
                val bottomPanelReservePx = with(density) { 140.dp.roundToPx() }
                val desiredLeftPadPx = mapPaddingPx
                val desiredRightPadPx = mapPaddingPx
                val desiredTopPadPx = mapPaddingPx
                val desiredBottomPadPx = mapPaddingPx + bottomPanelReservePx

                val maxPadX = (widthPx / 2) - 1
                val maxPadY = (heightPx / 2) - 1
                if (maxPadX <= 0 || maxPadY <= 0) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(boundsCenter, DEFAULT_ROUTE_FALLBACK_ZOOM)
                    )
                    viewModel.onCameraMove(boundsCenter)
                    viewModel.onRouteFitConsumed()
                    return@LaunchedEffect
                }

                val safeLeftPadPx = min(desiredLeftPadPx, maxPadX)
                val safeRightPadPx = min(desiredRightPadPx, maxPadX)
                val safeTopPadPx = min(desiredTopPadPx, maxPadY)
                val safeBottomPadPx = min(desiredBottomPadPx, maxPadY)

                val safeViewportWidthPx = widthPx - safeLeftPadPx - safeRightPadPx
                val safeViewportHeightPx = heightPx - safeTopPadPx - safeBottomPadPx
                val safeUniformPaddingPx = min(
                    min(safeLeftPadPx, safeRightPadPx),
                    min(safeTopPadPx, safeBottomPadPx)
                )

                if (safeViewportWidthPx <= 2 || safeViewportHeightPx <= 2) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(boundsCenter, DEFAULT_ROUTE_FALLBACK_ZOOM)
                    )
                    viewModel.onCameraMove(boundsCenter)
                    viewModel.onRouteFitConsumed()
                    return@LaunchedEffect
                }

                val bounds = LatLngBounds(
                    LatLng(fitTarget.bounds.minLat, fitTarget.bounds.minLng),
                    LatLng(fitTarget.bounds.maxLat, fitTarget.bounds.maxLng)
                )

                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds,
                            safeViewportWidthPx,
                            safeViewportHeightPx,
                            safeUniformPaddingPx
                        )
                    )
                    viewModel.onCameraMove(cameraPositionState.position.target)
                } catch (exception: Exception) {
                    if (exception.message?.contains("Additional camera padding") == true) {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(boundsCenter, DEFAULT_ROUTE_FALLBACK_ZOOM)
                        )
                        viewModel.onCameraMove(boundsCenter)
                    } else {
                        throw exception
                    }
                }
            }

            fitTarget.fallbackCenter != null -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        fitTarget.fallbackCenter,
                        DEFAULT_ROUTE_FALLBACK_ZOOM
                    )
                )
                viewModel.onCameraMove(fitTarget.fallbackCenter)
            }
        }

        viewModel.onRouteFitConsumed()
    }

    LaunchedEffect(uiState.currentMockLocation) {
        uiState.currentMockLocation?.let { mockMarkerState.position = it }
    }

    Scaffold(
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showSearchDialog = true },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search Location")
            }
        },
        topBar = {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Lat: %.6f".format(uiState.centerLocation.latitude))
                        Text(text = "Lng: %.6f".format(uiState.centerLocation.longitude))

                        val statusText = when {
                            uiState.simulationState == SimulationState.PLAYING -> "ROUTE_PLAYING (${"%.1f".format(uiState.speedKmh)} km/h)"
                            uiState.simulationState == SimulationState.PAUSED -> "ROUTE_PAUSED"
                            uiState.isMocking -> "MOCKING"
                            else -> "IDLE"
                        }
                        val statusColor = if (uiState.isMocking || uiState.simulationState == SimulationState.PLAYING) Color.Green else Color.Gray
                        Text(text = "Status: $statusText", color = statusColor)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { mapContainerSize = it }
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    properties = MapProperties(isMyLocationEnabled = false)
                ) {
                    uiState.savedLocations.forEach { location ->
                        Marker(
                            state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                            title = location.name,
                            onClick = {
                                viewModel.onCameraMove(LatLng(location.latitude, location.longitude))
                                false
                            },
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE
                            )
                        )
                    }

                    uiState.waypoints.forEachIndexed { index, point ->
                        Marker(
                            state = MarkerState(position = point),
                            title = "Point ${index + 1}",
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_CYAN
                            )
                        )
                    }


                    if (uiState.currentMockLocation != null) {
                        Marker(
                            state = mockMarkerState,
                            title = "Current Mock Location",
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE
                            )
                        )
                    }

                    if (uiState.waypoints.size > 1) {
                        Polyline(points = uiState.waypoints, color = Color.Blue, width = 10f)
                    }
                }

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Center",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    tint = Color.Red
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        if (uiState.waypoints.size >= 2) {
                            Button(onClick = {
                                routeNameInput = ""
                                showSaveRouteDialog = true
                            }) {
                                Text("Save Route")
                            }
                        }
                        Button(
                            onClick = { viewModel.clearRoute() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
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

                    androidx.compose.material3.Slider(
                        value = uiState.speedKmh.toFloat(),
                        onValueChange = { viewModel.setSpeed(it.toDouble()) },
                        valueRange = 0f..100f,
                        steps = 19
                    )
                    Text(
                        "Speed: ${"%.1f".format(uiState.speedKmh)} km/h",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

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


    if (showSearchDialog) {
        var searchInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Location") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            errorMessage = null
                        },
                        label = { Text("Query") },
                        supportingText = { Text("Supports Plus Code or lat,lng") },
                        singleLine = true,
                        isError = errorMessage != null
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = LocationQueryParser.parse(searchInput, uiState.centerLocation)
                        when (result) {
                            is ParseResult.Success -> {
                                val targetLatLng = result.parsedLocation.latLng
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f))
                                }
                                viewModel.onCameraMove(targetLatLng)
                                showSearchDialog = false
                            }
                            is ParseResult.Error -> {
                                errorMessage = result.message
                            }
                        }
                    }
                ) {
                    Text("Locate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSaveRouteDialog) {
        val normalized = routeNameInput.trim()
        AlertDialog(
            onDismissRequest = { showSaveRouteDialog = false },
            title = { Text("Save Route") },
            text = {
                OutlinedTextField(
                    value = routeNameInput,
                    onValueChange = { routeNameInput = it },
                    singleLine = true,
                    supportingText = { Text("1-40 chars") }
                )
            },
            confirmButton = {
                Button(
                    enabled = normalized.isNotEmpty() && normalized.length <= 40,
                    onClick = {
                        viewModel.saveCurrentRoute(normalized)
                        showSaveRouteDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRouteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val hasLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val hasNotification = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
            // If any requested permission is granted, we clear error.
            // If a permission is missing, ensuring logic in VM will catch it on next action.
            viewModel.clearError()
        }
    )

    if (uiState.mockError != null) {
        val error = uiState.mockError!!
        var isButtonEnabled by remember { mutableStateOf(false) }

        LaunchedEffect(error) {
            isButtonEnabled = false
            delay(500)
            isButtonEnabled = true
        }

        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(if (error is MockError.NotMockAppSelected || error is MockError.LocationPermissionMissing || error is MockError.NotificationPermissionMissing) "Permission Required" else "Error") },
            text = {
                Column {
                    Text(
                        text = when (error) {
                            is MockError.NotMockAppSelected -> "Please go to Developer Options -> Select mock location app -> Select this app."
                            is MockError.LocationPermissionMissing -> "This app requires location permission to function."
                            is MockError.NotificationPermissionMissing -> "This app requires notification permission to show foreground service controls."
                            is MockError.ProviderSetupFailed -> "Mock Engine Setup Failed: ${error.message}"
                            is MockError.SetLocationFailed -> "Set Location Failed: ${error.message}"
                            is MockError.ProviderTeardownFailed -> "Teardown Failed: ${error.message}"
                            is MockError.InvalidInput -> "Invalid Input: ${error.message}"
                            is MockError.PermissionCheckFailed -> "Permission Check Failed: ${error.message}"
                            is MockError.Unknown -> "Unknown Error: ${error.message}"
                        }
                    )
                }
            },
            confirmButton = {
                if (error is MockError.NotMockAppSelected) {
                    Button(
                        onClick = {
                            viewModel.clearError()
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent("android.settings.DEVELOPMENT_SETTINGS"))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        },
                        enabled = isButtonEnabled
                    ) {
                        Text(if (isButtonEnabled) "Go to Settings" else "Wait...")
                    }
                } else if (error is MockError.LocationPermissionMissing) {
                    Button(
                        onClick = {
                            viewModel.clearError()
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text("Grant Permission")
                    }
                } else if (error is MockError.NotificationPermissionMissing) {
                    Button(
                        onClick = {
                            viewModel.clearError()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                                )
                            }
                        }
                    ) {
                        Text("Grant Permission")
                    }
                } else {
                    TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                }
            },
            dismissButton = {
                if (error is MockError.NotMockAppSelected) {
                    TextButton(onClick = { viewModel.clearError() }) { Text("Cancel") }
                }
            }
        )
    }
}
