package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.SearchRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.utils.LocationQueryParser
import com.moooo_works.letsgogps.utils.ParseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 面板只準備選點/路線；所有模擬動作交給 VM 的受檢入口。 */
class FloatingCompanionController(
    private val mapState: StateFlow<MapUiState>,
    private val repository: LocationRepository,
    private val searchRepository: SearchRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onAction: (CompanionAction) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val mutableState = MutableStateFlow(FloatingCompanionState())
    val state = mutableState.asStateFlow()
    private var allSaved = mapState.value.savedLocations
    private var queryGeneration = 0L
    private var loadGeneration = 0L
    private var routeRevision = 0L
    private var lastRouteKey = activeRouteKey()
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var lastSearchStarted: Long? = null
    private val collectors = mutableListOf<Job>()

    init {
        collectors += scope.launch {
            mapState.map { it.savedLocations }.distinctUntilChanged().collect {
                allSaved = it
                refreshSaved()
            }
        }
        collectors += scope.launch {
            mapState.map { activeRouteKey(it) }.distinctUntilChanged().collect { key ->
                if (key != lastRouteKey) {
                    lastRouteKey = key
                    routeRevision++
                }
            }
        }
        collectors += scope.launch { repository.observeFolders().collect { folders ->
            mutableState.update { it.copy(folders = folders) }
        } }
        collectors += scope.launch { repository.observeRoutes().collect { routes ->
            mutableState.update { it.copy(routes = routes) }
        } }
        collectors += scope.launch { settingsRepository.observeStepQuotaUsedToday().collect { count ->
            mutableState.update { it.copy(dailySteps = count) }
        } }
        collectors += scope.launch { settingsRepository.observeStepDailyQuota().collect { quota ->
            mutableState.update { it.copy(dailyQuota = quota) }
        } }
        refreshSaved()
    }

    fun setExpanded(expanded: Boolean) { mutableState.update { it.copy(expanded = expanded) } }
    fun setTab(tab: CompanionTab) { mutableState.update { it.copy(tab = tab) } }
    fun setMessage(message: CompanionMessage?) { mutableState.update { it.copy(message = message) } }

    fun setQuery(query: String) {
        queryGeneration++
        searchJob?.cancel()
        mutableState.update { it.copy(query = query, searchResults = emptyList(), searching = false, message = null) }
        refreshSaved()
    }

    fun setFolder(folderId: Int?) {
        mutableState.update { it.copy(folderId = folderId) }
        refreshSaved()
    }

    fun setFavoritesOnly(favorites: Boolean) {
        mutableState.update { it.copy(favoritesOnly = favorites) }
        refreshSaved()
    }

    private fun refreshSaved() {
        val current = state.value
        val query = current.query.trim()
        val matches = allSaved.filter {
            it.name.contains(query, ignoreCase = true) &&
                (!current.favoritesOnly || it.isFavorite) &&
                (current.folderId == null || it.folderId == current.folderId)
        }
        mutableState.update { it.copy(savedLocations = matches) }
    }

    fun search() {
        val query = state.value.query.trim()
        if (query.isEmpty()) return
        val token = ++queryGeneration
        searchJob?.cancel()
        val reference = currentCompanionCoordinate(mapState.value) ?: mapState.value.centerLocation
        when (val parsed = LocationQueryParser.parse(query, reference)) {
            is ParseResult.Success -> {
                val point = parsed.parsedLocation.latLng
                select(GeocodedLocation(formatCompanionCoordinate(point), query, point))
                mutableState.update { it.copy(searchResults = emptyList(), searching = false, message = null) }
                return
            }
            is ParseResult.Error -> if (looksLikeCoordinates(query)) {
                mutableState.update { it.copy(searching = false, message = CompanionMessage.INVALID_COORDINATES) }
                return
            }
        }
        mutableState.update { it.copy(searching = true, message = null) }
        searchJob = scope.launch {
            val wait = lastSearchStarted?.let { (1_000L - (nowMillis() - it)).coerceAtLeast(0L) } ?: 0L
            if (wait > 0) delay(wait)
            if (token != queryGeneration) return@launch
            lastSearchStarted = nowMillis()
            val result = try { searchRepository.search(query) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { Result.failure(error) }
            if (token != queryGeneration || state.value.query.trim() != query) return@launch
            mutableState.update {
                it.copy(
                    searching = false,
                    searchResults = result.getOrNull().orEmpty(),
                    message = if (result.isFailure) CompanionMessage.SEARCH_FAILED else null
                )
            }
        }
    }

    fun select(location: GeocodedLocation) {
        cancelPendingActions()
        mutableState.update { it.copy(selectedLocation = location, replacement = null, message = null) }
    }

    fun queueSelected() {
        val selected = state.value.selectedLocation ?: return
        mutableState.update { it.copy(queuedLocations = it.queuedLocations + selected, message = null) }
    }

    fun applyQueue() {
        val points = state.value.queuedLocations.map { it.latLng }
        if (points.size < 2) { setMessage(CompanionMessage.NEED_TWO_POINTS); return }
        request(CompanionAction.UseRoute("", points.toList()))
    }

    fun requestLocate() { state.value.selectedLocation?.let { request(CompanionAction.Locate(it)) } }
    fun requestExplore() { state.value.selectedLocation?.let { request(CompanionAction.Explore(it)) } }

    fun requestLoadRoute(routeId: Int) {
        val token = ++loadGeneration
        loadJob?.cancel()
        synchronizeRouteRevision()
        val revision = routeRevision
        loadJob = scope.launch {
            val route = try { repository.getRouteWithPoints(routeId) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (token == loadGeneration) setMessage(CompanionMessage.SEARCH_FAILED)
                null
            }
            if (token != loadGeneration || route == null) return@launch
            synchronizeRouteRevision()
            if (revision != routeRevision) { setMessage(CompanionMessage.ROUTE_CHANGED); return@launch }
            val points = route.points.sortedBy { it.orderIndex }.map { LatLng(it.latitude, it.longitude) }
            if (points.size < 2) { setMessage(CompanionMessage.NEED_TWO_POINTS); return@launch }
            request(CompanionAction.UseRoute(route.route.name, points.toList()))
        }
    }

    private fun request(action: CompanionAction) {
        cancelPendingActions()
        if (!mapState.value.isProActive) { setMessage(CompanionMessage.NEED_PRO); return }
        synchronizeRouteRevision()
        val route = activeRouteKey()
        if (route != null) {
            mutableState.update { it.copy(replacement = CompanionReplacement(action, route, routeRevision)) }
        } else onAction(action)
    }

    fun cancelReplacement() { mutableState.update { it.copy(replacement = null) } }

    /** 新的明確操作與停止使仍在讀取的舊路線失效。 */
    fun cancelPendingActions() {
        loadGeneration++
        loadJob?.cancel()
        cancelReplacement()
    }

    fun confirmReplacement() {
        val pending = state.value.replacement ?: return
        synchronizeRouteRevision()
        cancelReplacement()
        if (!mapState.value.isProActive) { setMessage(CompanionMessage.NEED_PRO); return }
        if (pending.revision != routeRevision || pending.previousRoute != activeRouteKey()) {
            setMessage(CompanionMessage.ROUTE_CHANGED)
            return
        }
        onAction(pending.action)
    }

    fun saveSelected() {
        val selected = state.value.selectedLocation ?: return
        if (!mapState.value.isProActive) { setMessage(CompanionMessage.NEED_PRO); return }
        if (allSaved.any { abs(it.latitude - selected.latLng.latitude) < 0.0001 &&
                abs(it.longitude - selected.latLng.longitude) < 0.0001 }) {
            setMessage(CompanionMessage.ALREADY_SAVED)
            return
        }
        scope.launch {
            try {
                repository.saveLocation(SavedLocation(
                    name = selected.name, description = selected.address,
                    latitude = selected.latLng.latitude, longitude = selected.latLng.longitude,
                    isFavorite = true
                ))
                setMessage(CompanionMessage.SAVED)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { setMessage(CompanionMessage.SAVE_FAILED) }
        }
    }

    /** 明確關閉／VM清理使晚回結果與替換確認失效；收合不取消待辦。 */
    fun close() {
        queryGeneration++
        loadGeneration++
        searchJob?.cancel()
        loadJob?.cancel()
        mutableState.update { it.copy(expanded = false, searching = false, replacement = null) }
    }

    fun dispose() { close(); collectors.forEach { it.cancel() } }

    private fun activeRouteKey(map: MapUiState = mapState.value): List<LatLng>? =
        map.waypoints.toList().takeIf { map.isMocking && map.mapMode == MapMode.ROUTE }

    private fun synchronizeRouteRevision() {
        val key = activeRouteKey()
        if (key != lastRouteKey) { lastRouteKey = key; routeRevision++ }
    }

    private fun looksLikeCoordinates(query: String): Boolean =
        Regex("^[+-]?\\d+(?:\\.\\d+)?(?:\\s*,\\s*|\\s+)[+-]?\\d").containsMatchIn(query)
}
