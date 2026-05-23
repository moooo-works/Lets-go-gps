package com.moooo_works.letsgogps.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.utils.GeoDistanceMeters
import com.google.gson.annotations.SerializedName
import com.moooo_works.letsgogps.data.billing.RewardedAdManager
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

@Keep
data class ImportPreview(
    val uri: Uri,
    val schemaVersion: Int,
    val savedLocationsCount: Int,
    val routesCount: Int,
    val isGpx: Boolean = false,
    val foldersCount: Int = 0,
    val hasSettings: Boolean = false,
)

@Keep
data class ExportData(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = 3,
    @SerializedName("exportedAt")
    val exportedAt: Long? = null,
    @SerializedName("savedLocations")
    val savedLocations: List<ExportSavedLocation> = emptyList(),
    @SerializedName("routes")
    val routes: List<ExportRoute> = emptyList(),
    @SerializedName("folders")
    val folders: List<ExportFolder> = emptyList(),
    @SerializedName("settings")
    val settings: ExportSettings? = null,
)

@Keep
data class ExportSavedLocation(
    @SerializedName("id")
    val id: Int? = null,
    @SerializedName("name")
    val name: String,
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double,
    @SerializedName("createdAt")
    val createdAt: Long? = null,
    // Booleans default-init to false via Java primitive reflection — safe.
    @SerializedName("isFavorite")
    val isFavorite: Boolean = false,
    // String/List references must be nullable: Gson does not honor Kotlin
    // `= ""` defaults; missing fields in legacy v1/v2 backups deserialize
    // to null and would NPE downstream.
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("folderId")
    val folderId: Int? = null,
    @SerializedName("sortOrder")
    val sortOrder: Long? = null,
)

@Keep
data class ExportFolder(
    @SerializedName("id")
    val id: Int? = null,
    @SerializedName("name")
    val name: String,
    @SerializedName("createdAt")
    val createdAt: Long? = null,
)

@Keep
data class ExportSettings(
    @SerializedName("altitude")
    val altitude: Double? = null,
    @SerializedName("randomAltitude")
    val randomAltitude: Boolean? = null,
    @SerializedName("coordinateJitter")
    val coordinateJitter: Boolean? = null,
    @SerializedName("routeCornerSlowdown")
    val routeCornerSlowdown: Boolean? = null,
    @SerializedName("routeSpeed")
    val routeSpeed: Double? = null,
    @SerializedName("transportMode")
    val transportMode: String? = null,
    @SerializedName("mapMode")
    val mapMode: String? = null,
    @SerializedName("mapType")
    val mapType: String? = null,
    @SerializedName("clipboardHintEnabled")
    val clipboardHintEnabled: Boolean? = null,
)

@Keep
data class ExportRoutePoint(
    @SerializedName("lat")
    val lat: Double,
    @SerializedName("lng")
    val lng: Double,
    @SerializedName("dwellSeconds")
    val dwellSeconds: Int = 0
)

@Keep
data class ExportRoute(
    @SerializedName("routeId")
    val routeId: Int? = null,
    @SerializedName("name")
    val name: String,
    @SerializedName("points")
    val points: List<ExportRoutePoint> = emptyList(),
    @SerializedName("createdAt")
    val createdAt: Long? = null
)

sealed interface ProSectionState {
    object Free : ProSectionState
    data class AdUnlocked(val remainingMillis: Long, val watchAdEnabled: Boolean) : ProSectionState
    object Subscribed : ProSectionState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val settingsRepository: SettingsRepository,
    private val mockEngine: LocationMockEngine,
    private val proRepository: ProRepository,
    private val systemHealthCheck: com.moooo_works.letsgogps.domain.healthcheck.SystemHealthCheck,
    private val rewardedAdManager: RewardedAdManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    val isProActive: StateFlow<Boolean> = proRepository.isProActive

    /** Localized recurring price (e.g. "₱269.00", "$3.99") from Play Billing,
     *  or null until ProductDetails has loaded. */
    val subscriptionPrice: StateFlow<String?> = proRepository.subscriptionOffer
        .map { it?.formattedPrice }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            proRepository.subscriptionOffer.value?.formattedPrice,
        )

    /**
     * Injectable time source for tests. When non-null, replaces the default
     * 60-second ticker. Set this BEFORE first access to [proSection] (which is
     * lazy) so the override is picked up.
     */
    @VisibleForTesting
    internal var nowFlowOverride: Flow<Long>? = null

    private val nowTick: Flow<Long> by lazy {
        nowFlowOverride ?: flow {
            while (true) {
                emit(System.currentTimeMillis())
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    val proSection: StateFlow<ProSectionState> by lazy {
        combine(
            proRepository.isAdFreeActive,
            proRepository.adUnlockExpiryMillis,
            nowTick
        ) { adFree, expiry, now ->
            when {
                adFree -> ProSectionState.Subscribed
                expiry > now -> {
                    val remaining = expiry - now
                    ProSectionState.AdUnlocked(
                        remainingMillis = remaining,
                        watchAdEnabled = remaining < 18 * 3600_000L
                    )
                }
                else -> ProSectionState.Free
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProSectionState.Free)
    }

    fun watchRewardedAd(activity: Activity, onUnavailable: () -> Unit = {}) {
        rewardedAdManager.showAd(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    proRepository.grantAdUnlockHours(6)
                }
            },
            onUnavailable = onUnavailable
        )
    }

    private val _showProUpgrade = MutableStateFlow(false)
    val showProUpgrade: StateFlow<Boolean> = _showProUpgrade.asStateFlow()

    fun dismissProUpgrade() { _showProUpgrade.value = false }

    fun requestProUpgrade() { _showProUpgrade.value = true }

    fun launchBillingFlow(activity: Activity) {
        proRepository.launchBillingFlow(activity)
        dismissProUpgrade()
    }

    private val _mockPermissionStatus = MutableStateFlow<MockPermissionStatus>(MockPermissionStatus.NotAllowed)
    val mockPermissionStatus: StateFlow<MockPermissionStatus> = _mockPermissionStatus.asStateFlow()

    private val _healthCheckState = MutableStateFlow<com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState?>(null)
    val healthCheckState: StateFlow<com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState?> = _healthCheckState.asStateFlow()

    private val _showHealthCheck = MutableStateFlow(false)
    val showHealthCheck: StateFlow<Boolean> = _showHealthCheck.asStateFlow()

    fun openHealthCheck() {
        _healthCheckState.value = systemHealthCheck.refresh()
        _showHealthCheck.value = true
    }

    fun refreshHealthCheck() {
        _healthCheckState.value = systemHealthCheck.refresh()
    }

    fun dismissHealthCheck() {
        _showHealthCheck.value = false
    }

    val altitude = settingsRepository.observeAltitude()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15.0)

    val randomAltitude = settingsRepository.observeRandomAltitude()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val coordinateJitter = settingsRepository.observeCoordinateJitter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val routeCornerSlowdown = settingsRepository.observeRouteCornerSlowdown()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clipboardHintEnabled = settingsRepository.observeClipboardHintEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enableTimezoneCheck = settingsRepository.observeEnableTimezoneCheck()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setEnableTimezoneCheck(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEnableTimezoneCheck(enabled) }
    }

    fun setAltitude(value: Double) {
        viewModelScope.launch {
            settingsRepository.setAltitude(value)
        }
    }

    fun setRandomAltitude(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRandomAltitude(enabled)
        }
    }

    fun setCoordinateJitter(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCoordinateJitter(enabled)
        }
    }

    fun setRouteCornerSlowdown(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRouteCornerSlowdown(enabled)
        }
    }

    fun setClipboardHintEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardHintEnabled(enabled)
        }
    }

    init {
        refreshMockPermission()
        rewardedAdManager.preload()
    }

    fun refreshMockPermission() {
        _mockPermissionStatus.value = mockEngine.getMockPermissionStatus()
    }

    fun clearNonFavorites() {
        viewModelScope.launch {
            locationRepository.deleteNonFavorites()
        }
    }

    fun exportDataToUri(uri: Uri, includeSavedLocations: Boolean, includeRoutes: Boolean, onResult: (Boolean, String?) -> Unit) {
        if (!proRepository.isProActive.value) {
            onResult(false, "PRO_REQUIRED")
            return
        }
        viewModelScope.launch {
            try {
                val locations = locationRepository.getAllLocations().first()
                val routesSummaries = locationRepository.observeRoutes().first()

                val routes = if (!includeRoutes) emptyList() else routesSummaries.mapNotNull { summary ->
                    val routeWithPoints = locationRepository.getRouteWithPoints(summary.id)
                    routeWithPoints?.let { rwp ->
                        ExportRoute(
                            routeId = rwp.route.id,
                            name = rwp.route.name,
                            points = rwp.points.sortedBy { it.orderIndex }.map {
                                ExportRoutePoint(lat = it.latitude, lng = it.longitude, dwellSeconds = it.dwellSeconds)
                            },
                            createdAt = rwp.route.createdAt
                        )
                    }
                }

                val exportLocations = if (!includeSavedLocations) emptyList() else locations.map {
                    ExportSavedLocation(
                        id = it.id,
                        name = it.name,
                        lat = it.latitude,
                        lng = it.longitude,
                        createdAt = it.createdAt,
                        isFavorite = it.isFavorite,
                        description = it.description,
                        folderId = it.folderId,
                        sortOrder = it.sortOrder,
                    )
                }

                val foldersExport = if (!includeSavedLocations) emptyList()
                else locationRepository.observeFolders().first().map {
                    ExportFolder(id = it.id, name = it.name, createdAt = it.createdAt)
                }

                val settingsExport = ExportSettings(
                    altitude = settingsRepository.observeAltitude().first(),
                    randomAltitude = settingsRepository.observeRandomAltitude().first(),
                    coordinateJitter = settingsRepository.observeCoordinateJitter().first(),
                    routeCornerSlowdown = settingsRepository.observeRouteCornerSlowdown().first(),
                    routeSpeed = settingsRepository.observeRouteSpeed().first(),
                    transportMode = settingsRepository.observeTransportMode().first(),
                    mapMode = settingsRepository.observeMapMode().first(),
                    mapType = settingsRepository.observeMapType().first(),
                    clipboardHintEnabled = settingsRepository.observeClipboardHintEnabled().first(),
                )

                val exportData = ExportData(
                    schemaVersion = 3,
                    exportedAt = System.currentTimeMillis(),
                    savedLocations = exportLocations,
                    routes = routes,
                    folders = foldersExport,
                    settings = settingsExport,
                )

                val jsonString = gson.toJson(exportData)

                withContext(Dispatchers.IO) {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("openOutputStream returned null")
                    outputStream.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }

    fun parseImportData(uri: Uri, onResult: (Boolean, ImportPreview?, String?) -> Unit) {
        if (!proRepository.isProActive.value) {
            onResult(false, null, "PRO_REQUIRED")
            return
        }
        viewModelScope.launch {
            try {
                var fileContent = ""
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("openInputStream returned null")
                    inputStream.use { stream ->
                        fileContent = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                if (fileContent.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, null, "File is empty") }
                    return@launch
                }

                val isGpx = fileContent.trimStart().startsWith("<")

                if (isGpx) {
                    val exportData = try {
                        parseGpxContent(fileContent)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { onResult(false, null, "Invalid GPX: ${e.message}") }
                        return@launch
                    }
                    val preview = ImportPreview(
                        uri = uri,
                        schemaVersion = 0,
                        savedLocationsCount = exportData.savedLocations.size,
                        routesCount = exportData.routes.size,
                        isGpx = true
                    )
                    withContext(Dispatchers.Main) { onResult(true, preview, null) }
                    return@launch
                }

                val exportData = try {
                    gson.fromJson(fileContent, ExportData::class.java)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onResult(false, null, "Invalid JSON format: ${e.message}") }
                    return@launch
                }

                if (exportData == null) {
                    withContext(Dispatchers.Main) { onResult(false, null, "Invalid data") }
                    return@launch
                }
                if (exportData.schemaVersion > 3) {
                    withContext(Dispatchers.Main) { onResult(false, null, "Unsupported schema version: ${exportData.schemaVersion}") }
                    return@launch
                }

                val preview = ImportPreview(
                    uri = uri,
                    schemaVersion = exportData.schemaVersion,
                    savedLocationsCount = exportData.savedLocations?.size ?: 0,
                    routesCount = exportData.routes?.size ?: 0,
                    foldersCount = exportData.folders?.size ?: 0,
                    hasSettings = exportData.settings != null,
                )

                withContext(Dispatchers.Main) {
                    onResult(true, preview, null)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, null, e.message)
                }
            }
        }
    }

    fun applyImportData(preview: ImportPreview, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                var fileContent = ""
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(preview.uri)
                        ?: throw java.io.IOException("openInputStream returned null")
                    inputStream.use { stream ->
                        fileContent = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                val exportData = if (preview.isGpx) {
                    parseGpxContent(fileContent)
                } else {
                    gson.fromJson(fileContent, ExportData::class.java)
                }

                var importedLocations = 0
                var skippedLocations = 0
                var importedRoutes = 0
                var skippedRoutes = 0

                // Folders first — build a remap from exported id → newly-created/found id.
                // Same-name folders are merged (case-sensitive trim) so re-importing
                // the same backup doesn't proliferate duplicates.
                //
                // Gson ignores Kotlin's `= emptyList()` default; a missing
                // "folders"/"savedLocations" key in legacy v1/v2 backups
                // deserializes to null. Coalesce defensively here.
                val foldersList = exportData.folders ?: emptyList()
                val savedList = exportData.savedLocations ?: emptyList()
                val routesList = exportData.routes ?: emptyList()

                val existingFolders = locationRepository.observeFolders().first()
                val folderIdRemap = mutableMapOf<Int, Int>()
                foldersList.forEach { exported ->
                    val existing = existingFolders.firstOrNull { it.name.trim() == exported.name.trim() }
                    val newId = existing?.id ?: locationRepository.createFolder(exported.name.trim())
                    if (exported.id != null) folderIdRemap[exported.id] = newId
                }

                val existingLocations = locationRepository.getAllLocations().first()
                val distanceThresholdMeters = 20.0

                savedList.forEach { exportedLoc ->
                    val isDupById = exportedLoc.id != null &&
                        existingLocations.any { it.id == exportedLoc.id }
                    val isDupByDistance = existingLocations.any { existing ->
                        GeoDistanceMeters.haversineMeters(existing.latitude, existing.longitude, exportedLoc.lat, exportedLoc.lng) < distanceThresholdMeters
                    }

                    if (!isDupById && !isDupByDistance) {
                        val now = System.currentTimeMillis()
                        locationRepository.saveLocation(
                            SavedLocation(
                                name = exportedLoc.name,
                                latitude = exportedLoc.lat,
                                longitude = exportedLoc.lng,
                                isFavorite = exportedLoc.isFavorite,
                                description = exportedLoc.description.orEmpty(),
                                createdAt = exportedLoc.createdAt ?: now,
                                sortOrder = exportedLoc.sortOrder ?: now,
                                folderId = exportedLoc.folderId?.let { folderIdRemap[it] },
                            )
                        )
                        importedLocations++
                    } else {
                        skippedLocations++
                    }
                }

                val existingRoutesSummaries = locationRepository.observeRoutes().first()
                val existingRoutes = existingRoutesSummaries.mapNotNull {
                    locationRepository.getRouteWithPoints(it.id)
                }

                routesList.forEach { exportedRoute ->
                    val exportedName = exportedRoute.name
                    val exportedNameClean = exportedName.trim().lowercase()
                    val exportedPoints = exportedRoute.points

                    val routeSameName = existingRoutes.find { it.route.name.trim().lowercase() == exportedNameClean }

                    if (routeSameName != null) {
                        val existingPoints = routeSameName.points.sortedBy { it.orderIndex }
                        val isSamePoints = existingPoints.size == exportedPoints.size && existingPoints.zip(exportedPoints).all { (existingPt, exportedPt) ->
                            GeoDistanceMeters.haversineMeters(existingPt.latitude, existingPt.longitude, exportedPt.lat, exportedPt.lng) < 10.0
                        }

                        if (isSamePoints) {
                            skippedRoutes++
                        } else {
                            var newName = "${exportedName} (imported 2)"
                            var counter = 3
                            while (existingRoutes.any { it.route.name.trim().lowercase() == newName.trim().lowercase() }) {
                                newName = "${exportedName} (imported ${counter})"
                                counter++
                            }
                            insertRoute(locationRepository, newName, exportedPoints)
                            importedRoutes++
                        }
                    } else {
                        insertRoute(locationRepository, exportedName, exportedPoints)
                        importedRoutes++
                    }
                }

                exportData.settings?.let { applySettings(it) }

                val summaryMsg = context.getString(R.string.import_result_locations, importedLocations, skippedLocations) + "\n" +
                    context.getString(R.string.import_result_routes, importedRoutes, skippedRoutes)
                withContext(Dispatchers.Main) {
                    onResult(true, summaryMsg)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message)
                }
            }
        }
    }

    /**
     * Writes a backup's settings block back into preferences. Each field is
     * applied only when present so partial-schema backups don't wipe
     * settings absent from the file.
     */
    private suspend fun applySettings(s: ExportSettings) {
        s.altitude?.let { settingsRepository.setAltitude(it) }
        s.randomAltitude?.let { settingsRepository.setRandomAltitude(it) }
        s.coordinateJitter?.let { settingsRepository.setCoordinateJitter(it) }
        s.routeCornerSlowdown?.let { settingsRepository.setRouteCornerSlowdown(it) }
        s.routeSpeed?.let { settingsRepository.setRouteSpeed(it) }
        s.transportMode?.let { settingsRepository.setTransportMode(it) }
        s.mapMode?.let { settingsRepository.setMapMode(it) }
        s.mapType?.let { settingsRepository.setMapType(it) }
        s.clipboardHintEnabled?.let { settingsRepository.setClipboardHintEnabled(it) }
    }

    private fun parseGpxContent(content: String): ExportData {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(InputSource(StringReader(content)))

        fun Element.tagNameLocal(): String = localName ?: tagName.substringAfter(':')
        fun NodeList.elements(): List<Element> =
            (0 until length).mapNotNull { item(it) as? Element }
        fun Element.directChildText(localTag: String): String {
            val child = (0 until childNodes.length)
                .mapNotNull { childNodes.item(it) as? Element }
                .firstOrNull { it.tagNameLocal().equals(localTag, ignoreCase = true) }
            return child?.textContent?.trim().orEmpty()
        }
        fun Element.descendants(localTag: String): List<Element> {
            val namespaced = getElementsByTagNameNS("*", localTag).elements()
            return if (namespaced.isNotEmpty()) namespaced else getElementsByTagName(localTag).elements()
        }

        val waypoints = mutableListOf<ExportSavedLocation>()
        val routes = mutableListOf<ExportRoute>()

        val waypointNodes = document.getElementsByTagNameNS("*", "wpt").elements().ifEmpty {
            document.getElementsByTagName("wpt").elements()
        }
        waypointNodes.forEach { waypoint ->
            val lat = waypoint.getAttribute("lat").toDoubleOrNull()
            val lon = waypoint.getAttribute("lon").toDoubleOrNull()
            if (lat != null && lon != null) {
                waypoints.add(
                    ExportSavedLocation(
                        name = waypoint.directChildText("name").ifBlank { "Waypoint ${waypoints.size + 1}" },
                        lat = lat,
                        lng = lon
                    )
                )
            }
        }

        val trackNodes = document.getElementsByTagNameNS("*", "trk").elements().ifEmpty {
            document.getElementsByTagName("trk").elements()
        }
        trackNodes.forEach { track ->
            val points = track.descendants("trkpt").mapNotNull { point ->
                val lat = point.getAttribute("lat").toDoubleOrNull()
                val lon = point.getAttribute("lon").toDoubleOrNull()
                if (lat != null && lon != null) ExportRoutePoint(lat = lat, lng = lon) else null
            }
            if (points.isNotEmpty()) {
                routes.add(
                    ExportRoute(
                        name = track.directChildText("name").ifBlank { "Track ${routes.size + 1}" },
                        points = points
                    )
                )
            }
        }

        val routeNodes = document.getElementsByTagNameNS("*", "rte").elements().ifEmpty {
            document.getElementsByTagName("rte").elements()
        }
        routeNodes.forEach { route ->
            val points = route.descendants("rtept").mapNotNull { point ->
                val lat = point.getAttribute("lat").toDoubleOrNull()
                val lon = point.getAttribute("lon").toDoubleOrNull()
                if (lat != null && lon != null) ExportRoutePoint(lat = lat, lng = lon) else null
            }
            if (points.isNotEmpty()) {
                routes.add(
                    ExportRoute(
                        name = route.directChildText("name").ifBlank { "Route ${routes.size + 1}" },
                        points = points
                    )
                )
            }
        }

        return ExportData(schemaVersion = 0, savedLocations = waypoints, routes = routes)
    }

    private suspend fun insertRoute(locationRepository: LocationRepository, name: String, points: List<ExportRoutePoint>) {
        locationRepository.insertRouteWithPoints(
            name,
            points.mapIndexed { index, ep ->
                com.moooo_works.letsgogps.data.model.RoutePoint(
                    routeId = 0,
                    orderIndex = index,
                    latitude = ep.lat,
                    longitude = ep.lng,
                    dwellSeconds = ep.dwellSeconds
                )
            }
        )
    }

    fun generateDiagnostics(): String {
        val pm = context.packageManager
        val versionName = try {
            val pInfo = pm.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "Unknown"
        }
        val versionCode = try {
            val pInfo = pm.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            "Unknown"
        }

        val apiLevel = Build.VERSION.SDK_INT
        val targetSdk = context.applicationInfo.targetSdkVersion

        val mockStatus = mockStateRepository.mockStatus.value.name

        val notificationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val fgsLocationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return """
            Fake GPS Diagnostics
            -------------------
            Version Name: $versionName
            Version Code: $versionCode
            API Level: $apiLevel
            Target SDK: $targetSdk
            Mock Status: $mockStatus
            Notification Perm: $notificationPerm
            FGS Location Perm: $fgsLocationPerm
        """.trimIndent()
    }
}
