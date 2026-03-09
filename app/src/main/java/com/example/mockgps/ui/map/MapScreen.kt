package com.example.mockgps.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mockgps.utils.LocationQueryParser
import com.example.mockgps.utils.ParseResult
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.domain.repository.GeocodedLocation
import com.example.mockgps.utils.LatLngBoundsUtil
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var showSearchDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }
    val mockMarkerState = remember { MarkerState(position = uiState.centerLocation) }
    var mapContainerSize by remember { mutableStateOf(IntSize.Zero) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.routeCompletionEvent.collect {
            snackbarHostState.showSnackbar("路線模擬已完成 ✓")
        }
    }

    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val waypointIcons = remember(uiState.waypoints.size, primaryColorArgb) {
        uiState.waypoints.mapIndexed { index, _ ->
            val label = when (index) {
                0 -> "S"
                uiState.waypoints.size - 1 -> "E"
                else -> "${index + 1}"
            }
            createNumberedMarkerBitmap(label, primaryColorArgb)
        }
    }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
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
                            title = "路點 ${index + 1}",
                            icon = waypointIcons.getOrNull(index) ?: BitmapDescriptorFactory.defaultMarker(),
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
                        Polyline(points = uiState.waypoints, color = MaterialTheme.colorScheme.primary, width = 10f)
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
                        val isRouteSimulating = uiState.simulationState == SimulationState.PLAYING
                                || uiState.simulationState == SimulationState.PAUSED

                        // 新增路點：僅在 IDLE 時顯示
                        if (uiState.mapMode == MapMode.ROUTE && !isRouteSimulating) {
                            OutlinedButton(
                                onClick = { viewModel.addWaypoint() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("+ 新增路點") }
                        }

                        // 停止按鈕：PAUSED 時顯示，停止但保留路點
                        if (uiState.mapMode == MapMode.ROUTE && uiState.simulationState == SimulationState.PAUSED) {
                            OutlinedButton(
                                onClick = { viewModel.stopRoute() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("⏹ 停止") }
                        }

                        Button(
                            onClick = {
                                if (uiState.mapMode == MapMode.ROUTE) {
                                    when (uiState.simulationState) {
                                        SimulationState.PLAYING -> viewModel.pauseRoute()
                                        SimulationState.PAUSED -> viewModel.resumeRoute()
                                        SimulationState.IDLE -> if (uiState.waypoints.isNotEmpty()) viewModel.playRoute()
                                    }
                                } else {
                                    if (uiState.isMocking) viewModel.stopMocking()
                                    else viewModel.startMocking()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    uiState.simulationState == SimulationState.PLAYING -> MaterialTheme.colorScheme.error
                                    uiState.isMocking -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        ) {
                            Text(
                                when {
                                    uiState.mapMode == MapMode.ROUTE && uiState.simulationState == SimulationState.PLAYING -> "⏸ 暫停"
                                    uiState.mapMode == MapMode.ROUTE && uiState.simulationState == SimulationState.PAUSED -> "▶ 繼續"
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
            title = { Text("儲存路線") },
            text = {
                OutlinedTextField(
                    value = routeNameInput,
                    onValueChange = { routeNameInput = it },
                    singleLine = true,
                    supportingText = { Text("1-40 個字元") }
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
                    Text("儲存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveRouteDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
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
            title = { Text(if (error is MockError.NotMockAppSelected || error is MockError.LocationPermissionMissing || error is MockError.NotificationPermissionMissing) "需要權限" else "發生錯誤") },
            text = {
                Column {
                    Text(
                        text = when (error) {
                            is MockError.NotMockAppSelected -> "請前往開發者選項 → 選取模擬位置應用程式 → 選擇本應用程式。"
                            is MockError.LocationPermissionMissing -> "需要位置權限才能使用模擬功能。"
                            is MockError.NotificationPermissionMissing -> "需要通知權限以顯示前景服務控制項。"
                            is MockError.ProviderSetupFailed -> "Mock Engine 初始化失敗：${error.message}"
                            is MockError.SetLocationFailed -> "設定位置失敗：${error.message}"
                            is MockError.ProviderTeardownFailed -> "停止失敗：${error.message}"
                            is MockError.InvalidInput -> "輸入無效：${error.message}"
                            is MockError.PermissionCheckFailed -> "權限檢查失敗：${error.message}"
                            is MockError.Unknown -> "未知錯誤：${error.message}"
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
                        Text(if (isButtonEnabled) "開發者選項" else "請稍候…")
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
                        Text("授予權限")
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
                        Text("授予權限")
                    }
                } else {
                    TextButton(onClick = { viewModel.clearError() }) { Text("確定") }
                }
            },
            dismissButton = {
                if (error is MockError.NotMockAppSelected) {
                    TextButton(onClick = { viewModel.clearError() }) { Text("取消") }
                }
            }
        )
    }
}

private fun createNumberedMarkerBitmap(label: String, colorArgb: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val sizePx = 80
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (label.length > 1) 24f else 30f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, sizePx / 2f, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
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

    // Helper to perform the search or direct locate
    val performAction = { query: String ->
        val parseResult = LocationQueryParser.parse(query, uiState.centerLocation)
        if (parseResult is ParseResult.Success) {
            onSelectResult(GeocodedLocation(
                name = "經緯度定位",
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
                        "搜尋地點",
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
                            contentDescription = "關閉",
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
                            "搜尋地點、座標或 Plus Code...", 
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
                                Icon(Icons.Default.Close, contentDescription = "清除")
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
                                text = "搜尋失敗: ${uiState.searchError}",
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
                                    "輸入關鍵字開始搜尋",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        uiState.searchResults.isEmpty() && searchQuery.isNotEmpty() && !uiState.isSearching -> {
                            Text(
                                "找不到相關結果",
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
