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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.xmlpull.v1.XmlPullParser
import android.util.Xml

@Keep
data class ImportPreview(
    val uri: Uri,
    val schemaVersion: Int,
    val savedLocationsCount: Int,
    val routesCount: Int,
    val isGpx: Boolean = false
)

@Keep
data class ExportData(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = 2,
    @SerializedName("exportedAt")
    val exportedAt: Long? = null,
    @SerializedName("savedLocations")
    val savedLocations: List<ExportSavedLocation> = emptyList(),
    @SerializedName("routes")
    val routes: List<ExportRoute> = emptyList()
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
    val createdAt: Long? = null
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val settingsRepository: SettingsRepository,
    private val mockEngine: LocationMockEngine,
    private val proRepository: ProRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    val isProActive: StateFlow<Boolean> = proRepository.isProActive

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

    val altitude = settingsRepository.observeAltitude()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15.0)

    val randomAltitude = settingsRepository.observeRandomAltitude()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val coordinateJitter = settingsRepository.observeCoordinateJitter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    init {
        refreshMockPermission()
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
                        createdAt = it.createdAt
                    )
                }

                val exportData = ExportData(
                    schemaVersion = 2,
                    exportedAt = System.currentTimeMillis(),
                    savedLocations = exportLocations,
                    routes = routes
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
                if (exportData.schemaVersion > 2) {
                    withContext(Dispatchers.Main) { onResult(false, null, "Unsupported schema version: ${exportData.schemaVersion}") }
                    return@launch
                }

                val preview = ImportPreview(
                    uri = uri,
                    schemaVersion = exportData.schemaVersion,
                    savedLocationsCount = exportData.savedLocations.size,
                    routesCount = exportData.routes.size
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

                val existingLocations = locationRepository.getAllLocations().first()
                val distanceThresholdMeters = 20.0

                exportData.savedLocations.forEach { exportedLoc ->
                    val isDuplicate = existingLocations.any { existing ->
                        GeoDistanceMeters.haversineMeters(existing.latitude, existing.longitude, exportedLoc.lat, exportedLoc.lng) < distanceThresholdMeters
                    }

                    if (!isDuplicate) {
                        locationRepository.saveLocation(
                            SavedLocation(
                                name = exportedLoc.name,
                                latitude = exportedLoc.lat,
                                longitude = exportedLoc.lng,
                                createdAt = exportedLoc.createdAt ?: System.currentTimeMillis()
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

                exportData.routes.forEach { exportedRoute ->
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

    private fun parseGpxContent(content: String): ExportData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(content.reader())

        val waypoints = mutableListOf<ExportSavedLocation>()
        val routes = mutableListOf<ExportRoute>()

        var inWpt = false; var inTrk = false; var inRte = false
        var inTrkpt = false; var inRtept = false; var inMetadata = false

        var currentWptLat = 0.0; var currentWptLon = 0.0; var currentWptName = ""
        var currentTrkName = ""; var currentTrkPoints = mutableListOf<ExportRoutePoint>()
        var currentRteName = ""; var currentRtePoints = mutableListOf<ExportRoutePoint>()
        val currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentText.clear()
                    when (parser.name.lowercase()) {
                        "metadata" -> inMetadata = true
                        "wpt" -> {
                            inWpt = true
                            currentWptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentWptLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentWptName = ""
                        }
                        "trk" -> { inTrk = true; currentTrkName = ""; currentTrkPoints = mutableListOf() }
                        "trkpt" -> {
                            inTrkpt = true
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (lat != null && lon != null) currentTrkPoints.add(ExportRoutePoint(lat = lat, lng = lon))
                        }
                        "rte" -> { inRte = true; currentRteName = ""; currentRtePoints = mutableListOf() }
                        "rtept" -> {
                            inRtept = true
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (lat != null && lon != null) currentRtePoints.add(ExportRoutePoint(lat = lat, lng = lon))
                        }
                    }
                }
                XmlPullParser.TEXT -> currentText.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    val text = currentText.toString().trim()
                    when (parser.name.lowercase()) {
                        "metadata" -> inMetadata = false
                        "name" -> when {
                            inMetadata || inTrkpt || inRtept -> { /* skip */ }
                            inWpt -> currentWptName = text
                            inTrk -> currentTrkName = text
                            inRte -> currentRteName = text
                        }
                        "wpt" -> {
                            if (currentWptLat != 0.0 || currentWptLon != 0.0) {
                                waypoints.add(ExportSavedLocation(
                                    name = currentWptName.ifBlank { "Waypoint ${waypoints.size + 1}" },
                                    lat = currentWptLat,
                                    lng = currentWptLon
                                ))
                            }
                            inWpt = false
                        }
                        "trkpt" -> inTrkpt = false
                        "trk" -> {
                            if (currentTrkPoints.isNotEmpty()) {
                                routes.add(ExportRoute(
                                    name = currentTrkName.ifBlank { "Track ${routes.size + 1}" },
                                    points = currentTrkPoints.toList()
                                ))
                            }
                            inTrk = false
                        }
                        "rtept" -> inRtept = false
                        "rte" -> {
                            if (currentRtePoints.isNotEmpty()) {
                                routes.add(ExportRoute(
                                    name = currentRteName.ifBlank { "Route ${routes.size + 1}" },
                                    points = currentRtePoints.toList()
                                ))
                            }
                            inRte = false
                        }
                    }
                }
            }
            eventType = parser.next()
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
