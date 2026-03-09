package com.example.mockgps.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    properties = MapProperties(isMyLocationEnabled = false),
                    onMapClick = { latLng ->
                        when (uiState.mapMode) {
                            MapMode.SINGLE -> coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
                                viewModel.onCameraMove(latLng)
                            }
                            MapMode.ROUTE -> {
                                val isSimulating = uiState.simulationState == SimulationState.PLAYING
                                        || uiState.simulationState == SimulationState.PAUSED
                                if (!isSimulating) viewModel.addWaypointAt(latLng)
                            }
                        }
                    }
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
                        val isSimulating = uiState.simulationState == SimulationState.PLAYING
                                || uiState.simulationState == SimulationState.PAUSED
                        Marker(
                            state = MarkerState(position = point),
                            title = "Point ${index + 1}",
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_CYAN
                            ),
                            onClick = {
                                if (!isSimulating) viewModel.removeWaypointAt(index)
                                true
                            }
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

                Canvas(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                ) {
                    val stroke = 3.dp.toPx()
                    val arm = size.width
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(0f, cy - stroke / 2f),
                        size = Size(arm, stroke)
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(cx - stroke / 2f, 0f),
                        size = Size(stroke, arm)
                    )
                }

                val statusText = when {
                    uiState.simulationState == SimulationState.PLAYING ->
                        "PLAYING  ${"%.0f".format(uiState.speedKmh)} km/h"
                    uiState.simulationState == SimulationState.PAUSED -> "PAUSED"
                    uiState.isMocking -> "MOCKING"
                    else -> "IDLE"
                }
                val statusActive = uiState.isMocking || uiState.simulationState == SimulationState.PLAYING
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter)
                        .clickable { showSearchDialog = true },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "搜尋位置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${"%.4f".format(uiState.centerLocation.latitude)}° N," +
                                    "  ${"%.4f".format(uiState.centerLocation.longitude)}° E",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (statusActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            )
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (statusActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(durationMillis = 250)),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(MapMode.SINGLE to "單點定位", MapMode.ROUTE to "路線規劃").forEach { (mode, label) ->
                            val selected = uiState.mapMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.setMapMode(mode) },
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.mapMode == MapMode.ROUTE) {
                            OutlinedButton(
                                onClick = { viewModel.addWaypoint() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("+ 新增路點") }
                        }

                        Button(
                            onClick = {
                                if (uiState.mapMode == MapMode.ROUTE) {
                                    if (uiState.simulationState == SimulationState.PLAYING) viewModel.pauseRoute()
                                    else if (uiState.waypoints.isNotEmpty()) viewModel.playRoute()
                                } else {
                                    if (uiState.isMocking) viewModel.stopMocking()
                                    else viewModel.startMocking()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isMocking || uiState.simulationState == SimulationState.PLAYING)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                when {
                                    uiState.mapMode == MapMode.ROUTE && uiState.simulationState == SimulationState.PLAYING -> "⏸ 暫停路線"
                                    uiState.mapMode == MapMode.ROUTE && uiState.waypoints.isNotEmpty() -> "▶ 開始模擬"
                                    uiState.mapMode == MapMode.SINGLE && uiState.isMocking -> "⏹ 停止模擬"
                                    else -> "▶ 開始模擬"
                                }
                            )
                        }
                    }

                    if (uiState.mapMode == MapMode.ROUTE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${"%.0f".format(uiState.speedKmh)} km/h",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(52.dp)
                            )
                            androidx.compose.material3.Slider(
                                value = uiState.speedKmh.toFloat(),
                                onValueChange = { viewModel.setSpeed(it.toDouble()) },
                                valueRange = 0f..100f,
                                steps = 19,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val transportModeLabels = mapOf(
                                    TransportMode.WALKING to "🚶 步行",
                                    TransportMode.CYCLING to "🚲 自行車",
                                    TransportMode.DRIVING to "🚗 汽車"
                                )
                                TransportMode.values().forEach { mode ->
                                    FilterChip(
                                        selected = uiState.transportMode == mode,
                                        onClick = { viewModel.setTransportMode(mode) },
                                        label = {
                                            Text(
                                                transportModeLabels[mode] ?: mode.name,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (uiState.waypoints.size >= 2) {
                                    TextButton(onClick = {
                                        routeNameInput = ""
                                        showSaveRouteDialog = true
                                    }) { Text("儲存") }
                                }
                                if (uiState.waypoints.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearRoute() },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) { Text("清除") }
                                }
                            }
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
