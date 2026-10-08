package com.moooo_works.letsgogps.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation

@Composable
fun FloatingCompanionView(
    mapState: MapUiState,
    companionState: FloatingCompanionState,
    actions: FloatingCompanionActions,
    joystickContent: @Composable () -> Unit
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun releaseInput() { focus.clearFocus(force = true); keyboard?.hide(); actions.onInputFocus(false) }
    LaunchedEffect(companionState.expanded, companionState.tab) {
        if (!companionState.expanded || companionState.tab !in setOf(CompanionTab.SEARCH, CompanionTab.SAVED)) releaseInput()
    }
    val drag = Modifier.pointerInput(actions.onWindowDrag, actions.onWindowDragEnd) {
        detectDragGestures(onDragEnd = actions.onWindowDragEnd, onDragCancel = actions.onWindowDragEnd) { change, delta ->
            change.consume(); actions.onWindowDrag(delta.x, delta.y)
        }
    }
    if (!companionState.expanded) {
        Surface(shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
            Text(stringResource(R.string.floating_title), Modifier.then(drag).clickable { actions.onExpandedChange(true) }.padding(16.dp))
        }
        return
    }
    val config = LocalConfiguration.current
    val panelHeight = minOf(600, config.screenHeightDp - 100).coerceAtLeast(200).dp
    Surface(shape = MaterialTheme.shapes.large, shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 320.dp).width(320.dp).heightIn(max = panelHeight).imePadding()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.floating_title), Modifier.fillMaxWidth().then(drag).padding(8.dp), style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { releaseInput(); actions.onExpandedChange(false) }) { Text(stringResource(R.string.floating_collapse)) }
                TextButton(onClick = { releaseInput(); actions.onOpenApp() }) { Text(stringResource(R.string.floating_open_app)) }
                TextButton(onClick = { releaseInput(); actions.onClose() }) { Text(stringResource(R.string.floating_close)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                CompanionTab.entries.forEach { tab ->
                    FilterChip(selected = companionState.tab == tab, onClick = { releaseInput(); actions.onTabChange(tab) }, label = { Text(stringResource(tab.label())) })
                }
            }
            if (mapState.showStepSyncCreditDialog || mapState.showHealthCheck || mapState.showProUpgrade || mapState.mockError != null) {
                Text(stringResource(R.string.floating_blocked), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { releaseInput(); actions.onOpenApp() }) { Text(stringResource(R.string.floating_open_app)) }
            }
            if (companionState.tab == CompanionTab.MAP) {
                CompanionMap(mapState, companionState, actions)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val current = currentCompanionCoordinate(mapState)
            Text(stringResource(R.string.floating_current, current?.let(::formatCompanionCoordinate) ?: stringResource(R.string.floating_inactive)))
            TextButton(onClick = actions.onCopyCurrent, enabled = current != null) { Text(stringResource(R.string.floating_copy_current)) }
            Text(stringResource(R.string.floating_usage, companionState.dailySteps, companionState.dailyQuota, mapState.featureCredits))
            when (companionState.tab) {
                CompanionTab.SEARCH -> {
                    OutlinedTextField(value = companionState.query, onValueChange = actions.onQueryChange,
                        label = { Text(stringResource(R.string.floating_query)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { releaseInput(); actions.onSearch() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { actions.onInputFocus(it.isFocused) })
                    Row {
                        TextButton(onClick = { releaseInput(); actions.onSearch() }, enabled = !companionState.searching) { Text(stringResource(R.string.floating_search)) }
                        TextButton(onClick = { releaseInput(); actions.onPaste() }) { Text(stringResource(R.string.floating_paste)) }
                    }
                    if (companionState.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.floating_saved))
                    SavedRows(companionState, actions)
                    Text(stringResource(R.string.floating_places))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        itemsIndexed(companionState.searchResults, key = { index, place -> "${place.latLng.latitude}:${place.latLng.longitude}:$index" }) { _, place -> LocationRow(place, actions.onSelect) }
                    }
                }
                CompanionTab.SAVED -> {
                    OutlinedTextField(value = companionState.query, onValueChange = actions.onQueryChange,
                        label = { Text(stringResource(R.string.floating_query)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { releaseInput() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { actions.onInputFocus(it.isFocused) })
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = companionState.folderId == null, onClick = { actions.onFolderChange(null) }, label = { Text(stringResource(R.string.floating_all)) })
                        companionState.folders.forEach { folder -> FilterChip(selected = companionState.folderId == folder.id, onClick = { actions.onFolderChange(folder.id) }, label = { Text(folder.name) }) }
                    }
                    FilterChip(selected = companionState.favoritesOnly, onClick = { actions.onFavoritesChange(!companionState.favoritesOnly) }, label = { Text(stringResource(R.string.floating_favorites)) })
                    SavedRows(companionState, actions)
                }
                CompanionTab.ROUTES -> {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(companionState.routes, key = { it.id }) { route ->
                            TextButton(onClick = { actions.onLoadRoute(route.id) }) { Text(stringResource(R.string.floating_route_item, route.name, route.pointCount)) }
                        }
                    }
                    Row {
                        TextButton(onClick = actions.onPlayPause) { Text(stringResource(when (mapState.simulationState) { SimulationState.PLAYING -> R.string.route_pause; SimulationState.PAUSED -> R.string.action_resume; else -> R.string.route_play })) }
                        TextButton(onClick = actions.onStop) { Text(stringResource(R.string.route_stop)) }
                    }
                    Text(stringResource(R.string.floating_speed, mapState.speedKmh))
                    Slider(value = mapState.speedKmh.toFloat().coerceIn(ROUTE_SPEED_MIN_KMH, ROUTE_SPEED_MAX_KMH), onValueChange = { actions.onSpeedChange(it.toDouble()) }, valueRange = ROUTE_SPEED_MIN_KMH..ROUTE_SPEED_MAX_KMH)
                    TextButton(onClick = actions.onLoopChange) { Text(stringResource(when(mapState.loopMode) { LoopMode.NONE -> R.string.route_loop_none; LoopMode.LOOP -> R.string.route_loop_loop; LoopMode.BOUNCE -> R.string.route_loop_bounce })) }
                }
                CompanionTab.CONTROL -> {
                    joystickContent()
                    TextButton(onClick = actions.onExplore, enabled = companionState.selectedLocation != null) { Text(stringResource(R.string.floating_explore)) }
                }
                CompanionTab.MAP -> Unit
            }
            companionState.selectedLocation?.let { selected ->
                HorizontalDivider()
                Text(stringResource(R.string.floating_selected))
                Text(selected.name)
                if (selected.address.isNotBlank()) Text(selected.address)
                Text(formatCompanionCoordinate(selected.latLng))
                TextButton(onClick = actions.onCopySelected) { Text(stringResource(R.string.floating_copy_selected)) }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = actions.onLocate) { Text(stringResource(R.string.floating_locate)) }
                    TextButton(onClick = actions.onSave) { Text(stringResource(R.string.floating_save)) }
                    TextButton(onClick = actions.onQueue) { Text(stringResource(R.string.floating_queue)) }
                }
            }
            Text(stringResource(R.string.floating_queued, companionState.queuedLocations.size))
            TextButton(onClick = actions.onApplyQueue, enabled = companionState.queuedLocations.size >= 2) { Text(stringResource(R.string.floating_apply)) }
            if (companionState.replacement != null) {
                Text(stringResource(R.string.floating_replace))
                Row {
                    TextButton(onClick = actions.onConfirmReplacement) { Text(stringResource(R.string.floating_confirm)) }
                    TextButton(onClick = actions.onCancelReplacement) { Text(stringResource(R.string.floating_cancel)) }
                }
            }
            companionState.message?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun SavedRows(state: FloatingCompanionState, actions: FloatingCompanionActions) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
        items(state.savedLocations, key = { it.id }) { saved ->
            LocationRow(GeocodedLocation(saved.name, saved.description, LatLng(saved.latitude, saved.longitude)), actions.onSelect)
        }
    }
}

@Composable
private fun LocationRow(location: GeocodedLocation, select: (GeocodedLocation) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { select(location) }.padding(8.dp)) {
        Text(location.name, style = MaterialTheme.typography.titleSmall)
        if (location.address.isNotBlank()) Text(location.address, style = MaterialTheme.typography.bodySmall)
        Text(formatCompanionCoordinate(location.latLng), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CompanionMap(map: MapUiState, state: FloatingCompanionState, actions: FloatingCompanionActions) {
    val context = LocalContext.current
    remember(context) { MapsInitializer.initialize(context) }
    val start = state.selectedLocation?.latLng ?: currentCompanionCoordinate(map) ?: map.centerLocation
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(start, 15f) }
    val name = stringResource(R.string.floating_map_point)
    Text(stringResource(R.string.floating_map_hint), style = MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().height(140.dp)) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = false, mapType = map.mapType),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, rotationGesturesEnabled = false),
            onMapClick = { actions.onSelect(GeocodedLocation(name, "", it)) })
        MapCrosshair(Modifier.align(Alignment.Center))
    }
    Text(formatCompanionCoordinate(camera.position.target), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { actions.onSelect(GeocodedLocation(name, "", camera.position.target)) }) { Text(stringResource(R.string.floating_use_center)) }
}

private fun CompanionTab.label() = when(this) {
    CompanionTab.SEARCH -> R.string.floating_search
    CompanionTab.SAVED -> R.string.floating_saved
    CompanionTab.ROUTES -> R.string.nav_routes
    CompanionTab.CONTROL -> R.string.floating_control
    CompanionTab.MAP -> R.string.floating_map
}

private fun CompanionMessage.label() = when(this) {
    CompanionMessage.SEARCH_FAILED -> R.string.floating_msg_search_failed
    CompanionMessage.INVALID_COORDINATES -> R.string.floating_msg_invalid_coordinates
    CompanionMessage.SAVED -> R.string.floating_msg_saved
    CompanionMessage.ALREADY_SAVED -> R.string.floating_msg_already_saved
    CompanionMessage.SAVE_FAILED -> R.string.floating_msg_save_failed
    CompanionMessage.NEED_TWO_POINTS -> R.string.floating_msg_need_two_points
    CompanionMessage.ROUTE_CHANGED -> R.string.floating_msg_route_changed
    CompanionMessage.NEED_PRO -> R.string.floating_msg_need_pro
    CompanionMessage.UNKNOWN_LOCATION -> R.string.floating_msg_unknown_location
    CompanionMessage.COPIED -> R.string.floating_msg_copied
}
