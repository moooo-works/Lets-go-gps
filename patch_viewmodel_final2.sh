cat << 'INNER_EOF' > app/src/main/java/com/example/mockgps/ui/settings/SettingsViewModel.kt
package com.example.mockgps.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.RouteWithPoints
import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.repository.LocationRepository
import com.example.mockgps.domain.repository.MockStateRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportPreview(
    val uri: Uri,
    val schemaVersion: Int,
    val savedLocationsCount: Int,
    val routesCount: Int
)

data class ExportData(
    val schemaVersion: Int = 2,
    val exportedAt: Long? = null,
    val savedLocations: List<ExportSavedLocation> = emptyList(),
    val routes: List<ExportRoute> = emptyList()
)

data class ExportSavedLocation(
    val id: Int? = null,
    val name: String,
    val lat: Double,
    val lng: Double,
    val createdAt: Long? = null
)

data class ExportRoutePoint(
    val lat: Double,
    val lng: Double,
    val dwellSeconds: Int = 0
)

data class ExportRoute(
    val routeId: Int? = null,
    val name: String,
    val points: List<ExportRoutePoint> = emptyList(),
    val createdAt: Long? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportDataToUri(uri: Uri, includeSavedLocations: Boolean, includeRoutes: Boolean, onResult: (Boolean, String?) -> Unit) {
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
        viewModelScope.launch {
            try {
                var jsonString = ""
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw java.io.IOException("openInputStream returned null")
                    inputStream.use { stream ->
                        jsonString = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                if (jsonString.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, null, "File is empty") }
                    return@launch
                }

                val exportData = try {
                    gson.fromJson(jsonString, ExportData::class.java)
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
                var jsonString = ""
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(preview.uri)
                        ?: throw java.io.IOException("openInputStream returned null")
                    inputStream.use { stream ->
                        jsonString = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                val exportData = gson.fromJson(jsonString, ExportData::class.java)

                var importedLocations = 0
                var skippedLocations = 0
                var importedRoutes = 0
                var skippedRoutes = 0

                val existingLocations = locationRepository.getAllLocations().first()
                val distanceThresholdMeters = 20.0

                exportData.savedLocations.forEach { exportedLoc ->
                    val isDuplicate = existingLocations.any { existing ->
                        com.example.mockgps.utils.GeoDistanceMeters.haversineMeters(existing.latitude, existing.longitude, exportedLoc.lat, exportedLoc.lng) < distanceThresholdMeters
                    }

                    if (!isDuplicate) {
                        locationRepository.saveLocation(
                            SavedLocation(
                                name = exportedLoc.name ?: "Imported Location",
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
                    val exportedName = exportedRoute.name ?: "Imported Route"
                    val exportedNameClean = exportedName.trim().lowercase()
                    val exportedPoints = exportedRoute.points

                    val routeSameName = existingRoutes.find { it.route.name.trim().lowercase() == exportedNameClean }

                    if (routeSameName != null) {
                        val existingPoints = routeSameName.points.sortedBy { it.orderIndex }
                        val isSamePoints = existingPoints.size == exportedPoints.size && existingPoints.zip(exportedPoints).all { (existingPt, exportedPt) ->
                            com.example.mockgps.utils.GeoDistanceMeters.haversineMeters(existingPt.latitude, existingPt.longitude, exportedPt.lat, exportedPt.lng) < 10.0
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

                val summaryMsg = "Locations: ${importedLocations} imported, ${skippedLocations} skipped.\nRoutes: ${importedRoutes} imported, ${skippedRoutes} skipped."
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

    private suspend fun insertRoute(locationRepository: LocationRepository, name: String, points: List<ExportRoutePoint>) {
        locationRepository.insertRouteWithPoints(
            name,
            points.mapIndexed { index, ep ->
                com.example.mockgps.data.model.RoutePoint(
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

        val notificationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION.CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val fgsLocationPerm = if (Build.VERSION.SDK_INT >= Build.VERSION.CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return """
            MockGPS Diagnostics
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
INNER_EOF
