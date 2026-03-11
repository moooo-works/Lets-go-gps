package com.moooo_works.letsgogps.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import com.moooo_works.letsgogps.data.model.SavedLocation
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import com.google.android.gms.location.LocationServices
import com.moooo_works.letsgogps.ui.theme.Accent500
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.moooo_works.letsgogps.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.moooo_works.letsgogps.ui.ads.InterstitialAdManager
import com.moooo_works.letsgogps.ui.components.BannerAdView
import com.moooo_works.letsgogps.ui.pro.ProUpgradeDialog
import com.moooo_works.letsgogps.utils.LocationQueryParser
import com.moooo_works.letsgogps.utils.ParseResult
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.utils.LatLngBoundsUtil
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
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
    val activity = context as? android.app.Activity
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val interstitialAdManager = remember { InterstitialAdManager(context) }
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

    // Sync camera with UI state center location, but with threshold to avoid flickering during smooth joystick move
    LaunchedEffect(uiState.centerLocation) {
        val currentCameraTarget = cameraPositionState.position.target
        val distance = com.moooo_works.letsgogps.utils.GeoDistanceMeters.haversineMeters(
            currentCameraTarget.latitude, currentCameraTarget.longitude,
            uiState.centerLocation.latitude, uiState.centerLocation.longitude
        )
        // Only jump if distance is significant (e.g., > 500m) or if camera is not moving
        // This allows smooth movement without the camera "fighting" the user's manual drag or rapid updates
        if (!cameraPositionState.isMoving && distance > 1.0) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(uiState.centerLocation, cameraPositionState.position.zoom)
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

    Scaffold(contentWindowInsets = WindowInsets(0)) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, rotationGesturesEnabled = false),
                    properties = MapProperties(isMyLocationEnabled = false, mapType = uiState.mapType),
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
                            snippet = location.description.takeIf { it.isNotBlank() },
                            onClick = {
                                viewModel.onCameraMove(LatLng(location.latitude, location.longitude))
                                viewModel.selectLocation(location)
                                false
                            },
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                if (location.isFavorite)
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
                                else
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
                        .size(24.dp)
                        .align(Alignment.Center)
                ) {
                    val stroke = 2.dp.toPx()
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
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            contentDescription = stringResource(R.string.map_search_location),
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
                } // end search bar Box

                // My Location Button (TopEnd)
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 16.dp, top = 60.dp)
                ) {
                SmallFloatingActionButton(
                    onClick = {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                val target = com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude)
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(target, 15f)
                                    )
                                    viewModel.onCameraMove(target)
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.map_my_location))
                }
                } // end MyLocation Box

                // FAB Column (BottomEnd)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Map Type Toggle FAB
                    SmallFloatingActionButton(
                        onClick = { viewModel.toggleMapType() },
                        containerColor = if (uiState.mapType == com.google.maps.android.compose.MapType.HYBRID)
                            Accent500 else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.mapType == com.google.maps.android.compose.MapType.HYBRID)
                            Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.map_layer_toggle))
                    }

                    // Joystick Toggle FAB
                    SmallFloatingActionButton(
                        onClick = { viewModel.toggleJoystick() },
                        containerColor = if (uiState.isJoystickEnabled) Accent500 else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (uiState.isJoystickEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            if (uiState.isProActive) Icons.Default.ControlCamera else Icons.Default.Lock,
                            contentDescription = stringResource(R.string.map_joystick_toggle)
                        )
                    }
                }

                // 選中位置操作列
                uiState.selectedLocation?.let { selected ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 60.dp)
                    ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.deleteSelectedLocation() }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    if (selected.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.action_favorite),
                                    tint = if (selected.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.showEditLocationDialog() }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.dismissSelectedLocation() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    } // end action card Box
                }
            }

            if (!uiState.isProActive) {
                BannerAdView()
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
                        listOf(
                            MapMode.SINGLE to stringResource(R.string.map_mode_single),
                            MapMode.ROUTE to if (uiState.isProActive) stringResource(R.string.map_mode_route) else "🔒 ${stringResource(R.string.map_mode_route)}"
                        ).forEach { (mode, label) ->
                            val selected = uiState.mapMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (mode == MapMode.ROUTE && !uiState.isProActive) {
                                            viewModel.showProUpgradeDialog()
                                        } else {
                                            viewModel.setMapMode(mode)
                                        }
                                    },
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
                            ) { Text(stringResource(R.string.map_route_add_point)) }
                        }

                        Button(
                            onClick = {
                                if (uiState.mapMode == MapMode.ROUTE) {
                                    if (uiState.simulationState == SimulationState.PLAYING) viewModel.pauseRoute()
                                    else if (uiState.waypoints.isNotEmpty()) viewModel.playRoute()
                                } else {
                                    if (uiState.isMocking) {
                                        viewModel.stopMocking()
                                    } else if (!uiState.isProActive && activity != null) {
                                        interstitialAdManager.showAd(activity) { viewModel.startMocking() }
                                    } else {
                                        viewModel.startMocking()
                                    }
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
                                    uiState.mapMode == MapMode.ROUTE && uiState.simulationState == SimulationState.PLAYING -> "⏸ ${stringResource(R.string.map_route_pause)}"
                                    uiState.mapMode == MapMode.ROUTE && uiState.waypoints.isNotEmpty() -> "▶ ${stringResource(R.string.map_route_start)}"
                                    uiState.mapMode == MapMode.SINGLE && uiState.isMocking -> "⏹ ${stringResource(R.string.map_mock_stop)}"
                                    else -> "▶ ${stringResource(R.string.map_mock_start)}"
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
                                    TransportMode.WALKING to "🚶 ${stringResource(R.string.map_transport_walking)}",
                                    TransportMode.CYCLING to "🚲 ${stringResource(R.string.map_transport_cycling)}",
                                    TransportMode.DRIVING to "🚗 ${stringResource(R.string.map_transport_driving)}"
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
                                    }) { Text(stringResource(R.string.action_save)) }
                                }
                                if (uiState.waypoints.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearRoute() },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) { Text(stringResource(R.string.action_clear)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showProUpgrade) {
        ProUpgradeDialog(
            onDismiss = { viewModel.dismissProUpgrade() },
            onUpgrade = { activity?.let { viewModel.launchBillingFlow(it) } ?: viewModel.dismissProUpgrade() }
        )
    }

    val editTarget = uiState.selectedLocation
    if (uiState.showEditLocationDialog && editTarget != null) {
        EditLocationDialog(
            location = editTarget,
            onDismiss = { viewModel.dismissEditLocationDialog() },
            onConfirm = { name, description -> viewModel.updateLocationDetails(name, description) }
        )
    }

    if (showSearchDialog) {
        SearchDialog(
            uiState = uiState,
            onDismiss = { 
                showSearchDialog = false
                viewModel.clearSearchResults()
            },
            onSearch = { viewModel.searchLocations(it) },
            onSelectResult = {
                viewModel.selectSearchResult(it)
                showSearchDialog = false
            }
        )
    }

    if (showSaveRouteDialog) {
        val normalized = routeNameInput.trim()
        AlertDialog(
            onDismissRequest = { showSaveRouteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.route_save_title)) },
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
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRouteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    if (error is MockError.NotMockAppSelected ||
                        error is MockError.LocationPermissionMissing ||
                        error is MockError.NotificationPermissionMissing ||
                        error is MockError.FloatingWindowPermissionMissing) stringResource(R.string.error_permission_required) else stringResource(R.string.error_title)
                )
            },
            text = {
                Column {
                    Text(
                        text = when (error) {
                            is MockError.NotMockAppSelected -> stringResource(R.string.error_mock_app_not_selected)
                            is MockError.LocationPermissionMissing -> stringResource(R.string.error_location_permission)
                            is MockError.NotificationPermissionMissing -> stringResource(R.string.error_notification_permission)
                            is MockError.FloatingWindowPermissionMissing -> stringResource(R.string.error_floating_window)
                            is MockError.ProviderSetupFailed -> stringResource(R.string.error_provider_setup_failed, error.message)
                            is MockError.SetLocationFailed -> stringResource(R.string.error_set_location_failed, error.message)
                            is MockError.ProviderTeardownFailed -> stringResource(R.string.error_provider_teardown_failed, error.message)
                            is MockError.InvalidInput -> stringResource(R.string.error_invalid_input, error.message)
                            is MockError.PermissionCheckFailed -> stringResource(R.string.error_permission_check_failed, error.message)
                            is MockError.Unknown -> stringResource(R.string.error_unknown, error.message)
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
                        Text(if (isButtonEnabled) stringResource(R.string.action_go_to_settings) else stringResource(R.string.action_please_wait))
                    }
                } else if (error is MockError.FloatingWindowPermissionMissing) {
                    Button(
                        onClick = {
                            viewModel.clearError()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.action_go_to_enable))
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
                        Text(stringResource(R.string.action_grant_location))
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
                        Text(stringResource(R.string.action_grant_notification))
                    }
                } else {
                    TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismissButton = {
                if (error is MockError.NotMockAppSelected) {
                    TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }
}

@Composable
fun SearchDialog(
    uiState: MapUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectResult: (GeocodedLocation) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val searchLatLngLocationTitle = stringResource(R.string.search_latlng_location)

    // Helper to perform the search or direct locate
    val performAction = { query: String ->
        val parseResult = LocationQueryParser.parse(query, uiState.centerLocation)
        if (parseResult is ParseResult.Success) {
            onSelectResult(GeocodedLocation(
                name = searchLatLngLocationTitle,
                address = query,
                latLng = parseResult.parsedLocation.latLng
            ))
        } else {
            onSearch(query)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp, vertical = 40.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant, // Changed to surfaceVariant for better contrast against map
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant // Match text to background
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            stringResource(R.string.search_placeholder), 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        performAction(searchQuery)
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface, // Use surface for the input to pop out
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // Content Area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { // Added fillMaxWidth to ensure Box takes full space
                    when {
                        uiState.isSearching -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        uiState.searchError != null -> {
                            Text(
                                text = stringResource(R.string.search_failed, uiState.searchError),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center).fillMaxWidth(), // Centered text horizontally too
                                textAlign = TextAlign.Center
                            )
                        }
                        uiState.searchResults.isEmpty() && searchQuery.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center // Ensure vertical centering inside column
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.search_hint_start),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        uiState.searchResults.isEmpty() && searchQuery.isNotEmpty() && !uiState.isSearching -> {
                            Text(
                                stringResource(R.string.search_no_results),
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(uiState.searchResults) { result ->
                                    SearchResultItem(result = result, onClick = { onSelectResult(result) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(result: GeocodedLocation, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface, // Use surface for items to contrast with surfaceVariant background
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${"%.5f".format(result.latLng.latitude)}, ${"%.5f".format(result.latLng.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun EditLocationDialog(
    location: SavedLocation,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember(location.id) { mutableStateOf(location.name) }
    var description by remember(location.id) { mutableStateOf(location.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.location_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.location_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.location_edit_desc)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
