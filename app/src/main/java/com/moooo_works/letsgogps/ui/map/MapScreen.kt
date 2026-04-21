package com.moooo_works.letsgogps.ui.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.ui.ads.InterstitialAdManager
import com.moooo_works.letsgogps.ui.components.BannerAdView
import com.moooo_works.letsgogps.ui.onboarding.OnboardingSheet
import com.moooo_works.letsgogps.ui.pro.ProUpgradeDialog
import com.moooo_works.letsgogps.utils.LatLngBoundsUtil
import kotlin.math.min

private const val DEFAULT_ROUTE_FALLBACK_ZOOM = 15f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    searchViewModel: SearchViewModel,
    selectedLocation: LatLng? = null,
    onSelectedLocationConsumed: () -> Unit = {},
    onNavigateToSavedLocations: () -> Unit = {},
    onNavigateToRoutes: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val interstitialAdManager = remember { InterstitialAdManager(context) }

    // ── UI-only dialog visibility state ──────────────────────────────────────
    var showSaveRouteDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var routeNameInput by remember { mutableStateOf("") }

    // ── Map state ─────────────────────────────────────────────────────────────
    val mockMarkerState = remember { MarkerState(position = uiState.centerLocation) }
    var mapContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(uiState.centerLocation, 15f)
    }

    // ── Camera ↔ ViewModel sync ───────────────────────────────────────────────
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            viewModel.onCameraMove(cameraPositionState.position.target)
        }
    }

    // ── Permission refresh ────────────────────────────────────────────────────
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

    // Sync camera with UI state centre location (threshold prevents flickering during joystick move)
    LaunchedEffect(uiState.centerLocation) {
        val currentCameraTarget = cameraPositionState.position.target
        val distance = com.moooo_works.letsgogps.utils.GeoDistanceMeters.haversineMeters(
            currentCameraTarget.latitude, currentCameraTarget.longitude,
            uiState.centerLocation.latitude, uiState.centerLocation.longitude
        )
        if (!cameraPositionState.isMoving && distance > 1.0) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                uiState.centerLocation, cameraPositionState.position.zoom
            )
        }
    }

    // Jump to externally-selected location (from Saved Locations screen)
    LaunchedEffect(selectedLocation, uiState.routeFitRequestToken) {
        if (selectedLocation != null && uiState.routeFitRequestToken == null) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f))
            viewModel.onCameraMove(selectedLocation)
            onSelectedLocationConsumed()
        }
    }

    // Fit camera to current route waypoints
    LaunchedEffect(uiState.routeFitRequestToken, mapContainerSize) {
        if (uiState.routeFitRequestToken == null) return@LaunchedEffect

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
                    CameraUpdateFactory.newLatLngZoom(fitTarget.fallbackCenter, DEFAULT_ROUTE_FALLBACK_ZOOM)
                )
                viewModel.onCameraMove(fitTarget.fallbackCenter)
            }
        }

        viewModel.onRouteFitConsumed()
    }

    LaunchedEffect(uiState.currentMockLocation) {
        uiState.currentMockLocation?.let { mockMarkerState.position = it }
    }

    // ── Permission launcher (must be declared unconditionally) ────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.clearError() }
    )

    // ── Status text (computed here so both SearchBar and any future widget can use it) ──
    val statusText = when {
        uiState.simulationState == SimulationState.PLAYING ->
            stringResource(R.string.status_route_playing, "%.0f".format(uiState.speedKmh))
        uiState.simulationState == SimulationState.PAUSED -> stringResource(R.string.status_route_paused)
        uiState.isMocking -> stringResource(R.string.status_mocking)
        else -> stringResource(R.string.status_idle)
    }
    val statusActive = uiState.isMocking || uiState.simulationState == SimulationState.PLAYING

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold(contentWindowInsets = WindowInsets(0)) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Map container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { mapContainerSize = it }
            ) {
                MapGoogleMapContent(
                    uiState = uiState,
                    cameraPositionState = cameraPositionState,
                    mockMarkerState = mockMarkerState,
                    onSingleModeMapClick = { viewModel.onCameraMove(it) },
                    onRouteMapClick = { viewModel.addWaypointAt(it) },
                    onWaypointClick = { viewModel.removeWaypointAt(it) },
                    onSavedLocationClick = { loc ->
                        viewModel.onCameraMove(LatLng(loc.latitude, loc.longitude))
                        viewModel.selectLocation(loc)
                    }
                )

                MapCrosshair(modifier = Modifier.align(Alignment.Center))

                MapSearchBar(
                    centerLocation = uiState.centerLocation,
                    statusText = statusText,
                    statusActive = statusActive,
                    onClick = { showSearchDialog = true },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                MyLocationFab(
                    cameraPositionState = cameraPositionState,
                    onLocationFound = { viewModel.onCameraMove(it) },
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                MapActionFabs(
                    uiState = uiState,
                    onToggleMapType = { viewModel.toggleMapType() },
                    onToggleJoystick = { viewModel.toggleJoystick() },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )

                uiState.selectedLocation?.let { selected ->
                    SelectedLocationActionBar(
                        selectedLocation = selected,
                        onDelete = { viewModel.deleteSelectedLocation() },
                        onToggleFavorite = { viewModel.toggleFavorite() },
                        onEdit = { viewModel.showEditLocationDialog() },
                        onDismiss = { viewModel.dismissSelectedLocation() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            if (!uiState.isProActive) {
                BannerAdView()
            }

            MapBottomPanel(
                uiState = uiState,
                activity = activity,
                interstitialAdManager = interstitialAdManager,
                onSetMode = { viewModel.setMapMode(it) },
                onShowProUpgrade = { viewModel.showProUpgradeDialog() },
                onAddWaypoint = { viewModel.addWaypoint() },
                onStartMocking = { viewModel.startMocking() },
                onStopMocking = { viewModel.stopMocking() },
                onPlayRoute = { viewModel.playRoute() },
                onPauseRoute = { viewModel.pauseRoute() },
                onSetSpeed = { viewModel.setSpeed(it) },
                onSetTransportMode = { viewModel.setTransportMode(it) },
                onShowSaveRoute = {
                    routeNameInput = ""
                    showSaveRouteDialog = true
                },
                onClearRoute = { viewModel.clearRoute() }
            )
        }
    }

    // ── Overlay sheets & dialogs ──────────────────────────────────────────────
    if (uiState.showOnboarding) {
        OnboardingSheet(onDismiss = { viewModel.dismissOnboarding() })
    }

    if (uiState.showProUpgrade) {
        ProUpgradeDialog(
            onDismiss = { viewModel.dismissProUpgrade() },
            onUpgrade = {
                activity?.let { viewModel.launchBillingFlow(it) } ?: viewModel.dismissProUpgrade()
            }
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
            searchState = searchUiState,
            centerLocation = uiState.centerLocation,
            onDismiss = {
                showSearchDialog = false
                searchViewModel.clearSearchResults()
            },
            onSearch = { searchViewModel.searchLocations(it) },
            onSelectResult = {
                viewModel.selectSearchResult(it)
                searchViewModel.clearSearchResults()
                showSearchDialog = false
            }
        )
    }

    if (showSaveRouteDialog) {
        SaveRouteDialog(
            routeNameInput = routeNameInput,
            onNameChange = { routeNameInput = it },
            onConfirm = { name ->
                viewModel.saveCurrentRoute(name)
                showSaveRouteDialog = false
            },
            onDismiss = { showSaveRouteDialog = false }
        )
    }

    if (uiState.mockError != null) {
        MockErrorDialog(
            error = uiState.mockError!!,
            onClearError = { viewModel.clearError() },
            onRequestPermissions = { permissions -> permissionLauncher.launch(permissions) }
        )
    }
}
