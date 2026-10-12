package com.moooo_works.letsgogps.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
    var inputFocused by remember { mutableStateOf(false) }
    fun releaseInput() { inputFocused = false; focus.clearFocus(force = true); keyboard?.hide(); actions.onInputFocus(false) }
    LaunchedEffect(companionState.expanded, companionState.tab) {
        if (!companionState.expanded || companionState.tab !in setOf(CompanionTab.SEARCH, CompanionTab.SAVED)) releaseInput()
    }
    val windowDrag by rememberUpdatedState(actions.onWindowDrag)
    val windowDragEnd by rememberUpdatedState(actions.onWindowDragEnd)
    val drag = Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = { windowDragEnd() }, onDragCancel = { windowDragEnd() }) { change, delta ->
            change.consume(); windowDrag(delta.x, delta.y)
        }
    }
    val colors = MaterialTheme.colorScheme
    val success = if (colors.surface.luminance() < .5f) Color(0xFF7DDC9A) else Color(0xFF1E7B3C)
    val blocked = mapState.showStepSyncCreditDialog || mapState.showHealthCheck || mapState.showProUpgrade || mapState.mockError != null
    if (!companionState.expanded) {
        Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp, color = colors.surface.copy(alpha = .90f),
            border = BorderStroke(1.5.dp, colors.primary)) {
            Box(Modifier.size(48.dp).then(drag).clickable { actions.onExpandedChange(true) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.NearMe, stringResource(R.string.floating_title), tint = colors.primary, modifier = Modifier.size(24.dp))
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(8.dp)
                    .background(if (blocked) colors.error else if (mapState.isMocking) success else colors.onSurfaceVariant, CircleShape))
            }
        }
        return
    }
    val config = LocalConfiguration.current
    val panelWidth = minOf(358, config.screenWidthDp - 32).coerceAtLeast(280).dp
    val panelHeight = minOf(660, config.screenHeightDp - 80).coerceAtLeast(220).dp
    val compact = config.screenHeightDp < 460
    val selected = companionState.selectedLocation.takeIf { companionState.tab == CompanionTab.MAP }
    val pinnedSelection = selected != null && !compact
    val keyboardVisible = inputFocused && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bodyHeight = (panelHeight - if (pinnedSelection) 246.dp else if (keyboardVisible) 72.dp else 128.dp).coerceAtLeast(44.dp)
    val current = currentCompanionCoordinate(mapState)
    var menuOpen by remember { mutableStateOf(false) }
    val select: (GeocodedLocation) -> Unit = { releaseInput(); actions.onSelect(it); actions.onTabChange(CompanionTab.MAP) }
    Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 12.dp, color = colors.surface.copy(alpha = .82f), contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.width(panelWidth).heightIn(max = panelHeight).then(if (inputFocused) Modifier.imePadding() else Modifier)) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(28.dp).height(48.dp).then(drag), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DragIndicator, stringResource(R.string.floating_title), Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).then(drag)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(if (blocked) colors.error else if (mapState.isMocking) success else colors.onSurfaceVariant, CircleShape))
                        Text(stringResource(if (mapState.isMocking) R.string.floating_active else R.string.floating_inactive), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(current?.let(::formatCompanionCoordinate) ?: stringResource(R.string.floating_title), fontSize = 12.sp,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { releaseInput(); actions.onExpandedChange(false) }) { Icon(Icons.Default.CloseFullscreen, stringResource(R.string.floating_collapse), Modifier.size(20.dp)) }
                Box {
                    IconButton(onClick = { releaseInput(); menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.joystick_menu), Modifier.size(20.dp)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surface, tonalElevation = 0.dp) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.floating_copy_current)) }, onClick = { menuOpen = false; actions.onCopyCurrent() }, enabled = current != null)
                        DropdownMenuItem(text = { Text(stringResource(R.string.floating_open_app)) }, onClick = { menuOpen = false; actions.onOpenApp() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.floating_close), color = colors.error) }, onClick = { menuOpen = false; actions.onClose() })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = bodyHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (blocked) {
                    Text(stringResource(R.string.floating_blocked), color = colors.error, fontSize = 13.sp)
                    TextButton(onClick = { releaseInput(); actions.onOpenApp() }) { Text(stringResource(R.string.floating_open_app)) }
                }
                when (companionState.tab) {
                    CompanionTab.SEARCH -> {
                        CompanionSearchField(companionState, actions, onFocus = { inputFocused = it; actions.onInputFocus(it) }, submit = { releaseInput(); actions.onSearch() }, paste = { releaseInput(); actions.onPaste() })
                        Text(stringResource(R.string.floating_query), Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = colors.onSurfaceVariant)
                        if (companionState.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (companionState.savedLocations.isNotEmpty()) {
                            Text(stringResource(R.string.floating_saved), Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = colors.onSurfaceVariant)
                            SavedRows(companionState, actions, select)
                        }
                        if (companionState.searchResults.isNotEmpty()) {
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
                                itemsIndexed(companionState.searchResults, key = { index, place -> "${place.latLng.latitude}:${place.latLng.longitude}:$index" }) { _, place ->
                                    LocationRow(place, companionState.selectedLocation?.latLng == place.latLng, select, actions.onCopyLocation)
                                }
                            }
                        }
                    }
                    CompanionTab.SAVED -> {
                        CompanionSearchField(companionState, actions, onFocus = { inputFocused = it; actions.onInputFocus(it) }, submit = { releaseInput() }, paste = { releaseInput(); actions.onPaste() })
                        var foldersOpen by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                FilledTonalButton(onClick = { releaseInput(); foldersOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(8.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface)) {
                                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                    Text(companionState.folders.firstOrNull { it.id == companionState.folderId }?.name ?: stringResource(R.string.floating_all), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                                }
                                DropdownMenu(foldersOpen, { foldersOpen = false }, containerColor = colors.surface, tonalElevation = 0.dp) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.floating_all)) }, onClick = { foldersOpen = false; actions.onFolderChange(null) })
                                    companionState.folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) }, onClick = { foldersOpen = false; actions.onFolderChange(folder.id) }) }
                                }
                            }
                            FilterChip(selected = companionState.favoritesOnly, onClick = { releaseInput(); actions.onFavoritesChange(!companionState.favoritesOnly) },
                                leadingIcon = { Icon(Icons.Default.StarBorder, null, Modifier.size(18.dp)) }, label = { Text(stringResource(R.string.floating_favorites), fontSize = 12.sp) })
                        }
                        SavedRows(companionState, actions, select)
                    }
                    CompanionTab.ROUTES -> {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 168.dp)) {
                            items(companionState.routes, key = { it.id }) { route ->
                                Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { actions.onLoadRoute(route.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.Route, null, Modifier.size(20.dp), tint = colors.primary)
                                        Text(stringResource(R.string.floating_route_item, route.name, route.pointCount), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceVariant) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    val (modeIcon, modeLabel) = when (mapState.loopMode) {
                                        LoopMode.NONE -> Icons.Default.ArrowForward to stringResource(R.string.route_loop_none)
                                        LoopMode.LOOP -> Icons.Default.Repeat to stringResource(R.string.route_loop_loop)
                                        LoopMode.BOUNCE -> Icons.Default.SyncAlt to stringResource(R.string.route_loop_bounce)
                                    }
                                    val switchMode = stringResource(R.string.route_loop_cd)
                                    FilledTonalButton(onClick = actions.onLoopChange,
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = switchMode; stateDescription = modeLabel },
                                        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (mapState.loopMode == LoopMode.NONE) colors.surface else colors.secondaryContainer,
                                            contentColor = if (mapState.loopMode == LoopMode.NONE) colors.onSurfaceVariant else colors.primary)) {
                                        Icon(modeIcon, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(modeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = actions.onStop) { Icon(Icons.Default.Stop, stringResource(R.string.route_stop), tint = colors.error) }
                                    FilledIconButton(onClick = actions.onPlayPause, modifier = Modifier.size(56.dp)) {
                                        Icon(if (mapState.simulationState == SimulationState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            stringResource(when(mapState.simulationState) { SimulationState.PLAYING -> R.string.route_pause; SimulationState.PAUSED -> R.string.action_resume; else -> R.string.route_play }))
                                    }
                                }
                                Text(stringResource(R.string.floating_speed, mapState.speedKmh), fontSize = 13.sp)
                                Slider(value = mapState.speedKmh.toFloat().coerceIn(ROUTE_SPEED_MIN_KMH, ROUTE_SPEED_MAX_KMH), onValueChange = { actions.onSpeedChange(it.toDouble()) }, valueRange = ROUTE_SPEED_MIN_KMH..ROUTE_SPEED_MAX_KMH)
                            }
                        }
                    }
                    CompanionTab.CONTROL -> {
                        joystickContent()
                        FilledTonalButton(onClick = actions.onExplore, enabled = companionState.selectedLocation != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface)) {
                            Icon(Icons.Default.Explore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.floating_explore), fontSize = 13.sp)
                        }
                    }
                    CompanionTab.MAP -> {
                        if (selected != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { actions.onTabChange(CompanionTab.SEARCH) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.floating_search)) }
                                Text(selected.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        CompanionMap(mapState, companionState, actions)
                        selected?.let {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.floating_selected), fontSize = 12.sp, color = colors.onSurfaceVariant)
                                Text(formatCompanionCoordinate(it.latLng), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                if (it.address.isNotBlank()) Text(it.address, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (!pinnedSelection) SelectionActions(actions)
                        }
                    }
                }
                if (companionState.queuedLocations.isNotEmpty()) {
                    Text(stringResource(R.string.floating_queued, companionState.queuedLocations.size), fontSize = 12.sp)
                    TextButton(onClick = actions.onApplyQueue, enabled = companionState.queuedLocations.size >= 2) { Text(stringResource(R.string.floating_apply)) }
                }
                if (companionState.replacement != null) {
                    Text(stringResource(R.string.floating_replace), color = colors.error)
                    Row {
                        TextButton(onClick = actions.onConfirmReplacement) { Text(stringResource(R.string.floating_confirm)) }
                        TextButton(onClick = actions.onCancelReplacement) { Text(stringResource(R.string.floating_cancel)) }
                    }
                }
                companionState.message?.let { Text(stringResource(it.label()), color = colors.primary, fontSize = 12.sp) }
                Text(stringResource(R.string.floating_usage, companionState.dailySteps, companionState.dailyQuota, mapState.featureCredits), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            if (pinnedSelection) SelectionActions(actions)
            if (!keyboardVisible) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CompanionTab.entries.forEach { tab ->
                    val selectedTab = companionState.tab == tab
                    val tint = if (selectedTab) colors.primary else colors.onSurfaceVariant
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = if (selectedTab) colors.secondaryContainer else Color.Transparent) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selectedTab, role = Role.Tab, onClick = { releaseInput(); actions.onTabChange(tab) }).padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(tab.icon(), stringResource(tab.label()), Modifier.size(22.dp), tint = tint)
                            Text(stringResource(tab.shortLabel()), fontSize = 12.sp, color = tint, fontWeight = if (selectedTab) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CompanionSearchField(state: FloatingCompanionState, actions: FloatingCompanionActions, onFocus: (Boolean) -> Unit, submit: () -> Unit, paste: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceVariant, border = BorderStroke(1.5.dp, colors.primary)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = submit, enabled = !state.searching) { Icon(Icons.Default.Search, stringResource(R.string.floating_search), Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
            BasicTextField(state.query, actions.onQueryChange, singleLine = true, cursorBrush = SolidColor(colors.primary), textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).onFocusChanged { onFocus(it.isFocused) }, decorationBox = { inner ->
                    Box {
                        if (state.query.isEmpty()) Text(stringResource(R.string.floating_query), color = colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                })
            IconButton(onClick = { actions.onQueryChange("") }, enabled = state.query.isNotEmpty()) { Icon(Icons.Default.Close, stringResource(R.string.map_search_clear), Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
            IconButton(onClick = paste) { Icon(Icons.Default.ContentPaste, stringResource(R.string.floating_paste), Modifier.size(20.dp), tint = colors.primary) }
        }
    }
}

@Composable
private fun SelectionActions(actions: FloatingCompanionActions) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((label, icon, click) in listOf(Triple(R.string.floating_copy, Icons.Default.ContentCopy, actions.onCopySelected), Triple(R.string.floating_save, Icons.Default.BookmarkAdd, actions.onSave), Triple(R.string.floating_queue, Icons.Default.PlaylistAdd, actions.onQueue))) {
                FilledTonalButton(onClick = click, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(6.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(label), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Button(onClick = actions.onLocate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.NearMe, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.floating_locate), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SavedRows(state: FloatingCompanionState, actions: FloatingCompanionActions, select: (GeocodedLocation) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 224.dp)) {
        items(state.savedLocations, key = { it.id }) { saved ->
            val location = GeocodedLocation(saved.name, saved.description, LatLng(saved.latitude, saved.longitude))
            LocationRow(location, state.selectedLocation?.latLng == location.latLng, select, actions.onCopyLocation, saved.isFavorite)
        }
    }
}

@Composable
private fun LocationRow(location: GeocodedLocation, selected: Boolean, select: (GeocodedLocation) -> Unit, copy: (LatLng) -> Unit, favorite: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = if (selected) colors.surfaceVariant else Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { select(location) }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(if (favorite) Icons.Default.Star else Icons.Default.LocationOn, null, Modifier.size(18.dp), tint = colors.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(location.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(location.address.ifBlank { formatCompanionCoordinate(location.latLng) }, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { copy(location.latLng) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.floating_copy_selected), Modifier.size(20.dp), tint = colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CompanionMap(map: MapUiState, state: FloatingCompanionState, actions: FloatingCompanionActions) {
    val context = LocalContext.current
    remember(context) { MapsInitializer.initialize(context) }
    val start = state.selectedLocation?.latLng ?: currentCompanionCoordinate(map) ?: map.centerLocation
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(start, 15f) }
    val name = stringResource(R.string.floating_map_point)
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = false, mapType = map.mapType),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, rotationGesturesEnabled = false), onMapClick = { actions.onSelect(GeocodedLocation(name, "", it)) }) {
                state.selectedLocation?.let { point ->
                    val markerState = remember { MarkerState(position = point.latLng) }
                    SideEffect { markerState.position = point.latLng }
                    Marker(state = markerState, title = point.name,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                }
            }
        if (state.selectedLocation == null) MapCrosshair(Modifier.align(Alignment.Center))
        Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
                IconButton(onClick = { camera.move(CameraUpdateFactory.zoomIn()) }) { Icon(Icons.Default.Add, stringResource(R.string.floating_zoom_in)) }
                IconButton(onClick = { camera.move(CameraUpdateFactory.zoomOut()) }) { Icon(Icons.Default.Remove, stringResource(R.string.floating_zoom_out)) }
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = { camera.move(CameraUpdateFactory.newLatLng(currentCompanionCoordinate(map) ?: map.centerLocation)) }) { Icon(Icons.Default.MyLocation, stringResource(R.string.map_my_location)) }
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
            IconButton(onClick = { actions.onSelect(GeocodedLocation(name, "", camera.position.target)) }) { Icon(Icons.Default.Check, stringResource(R.string.floating_use_center)) }
        }
    }
    if (state.selectedLocation == null) Text(stringResource(R.string.floating_map_hint), Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun CompanionTab.icon() = when(this) {
    CompanionTab.SEARCH -> Icons.Default.Search
    CompanionTab.SAVED -> Icons.Default.StarBorder
    CompanionTab.ROUTES -> Icons.Default.Route
    CompanionTab.CONTROL -> Icons.Default.Gamepad
    CompanionTab.MAP -> Icons.Default.Map
}

private fun CompanionTab.shortLabel() = when(this) {
    CompanionTab.SAVED -> R.string.floating_tab_saved
    CompanionTab.MAP -> R.string.nav_map
    else -> label()
}
