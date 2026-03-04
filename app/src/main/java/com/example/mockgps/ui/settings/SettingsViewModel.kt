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

data class ExportData(
    val schemaVersion: Int = 1,
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

    fun exportDataToUri(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val locations = locationRepository.getAllLocations().first()
                val routesSummaries = locationRepository.observeRoutes().first()

                val routes = routesSummaries.mapNotNull { summary ->
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

                val exportLocations = locations.map {
                    ExportSavedLocation(
                        id = it.id,
                        name = it.name,
                        lat = it.latitude,
                        lng = it.longitude,
                        createdAt = it.createdAt
                    )
                }

                val exportData = ExportData(
                    schemaVersion = 1,
                    savedLocations = exportLocations,
                    routes = routes
                )

                val jsonString = gson.toJson(exportData)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }

                onResult(true, null)

            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun importDataFromUri(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                var jsonString = ""
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    jsonString = inputStream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                if (jsonString.isBlank()) {
                    onResult(false, "File is empty")
                    return@launch
                }

                val exportData = try {
                    gson.fromJson(jsonString, ExportData::class.java)
                } catch (e: Exception) {
                    onResult(false, "Invalid JSON format: ${e.message}")
                    return@launch
                }

                if (exportData == null || exportData.schemaVersion != 1) {
                    onResult(false, "Unsupported schema version or invalid data")
                    return@launch
                }

                var importedLocations = 0
                var skippedLocations = 0
                var importedRoutes = 0
                var skippedRoutes = 0

                val existingLocations = locationRepository.getAllLocations().first()
                val epsilon = 0.0001

                exportData.savedLocations.forEach { exportedLoc ->
                    val isDuplicate = existingLocations.any { existing ->
                        Math.abs(existing.latitude - exportedLoc.lat) < epsilon &&
                        Math.abs(existing.longitude - exportedLoc.lng) < epsilon
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
                    val exportedPointsHash = exportedRoute.points.map { "${it.lat},${it.lng}" }.hashCode()
                    val exportedName = exportedRoute.name ?: "Imported Route"

                    val isDuplicate = existingRoutes.any { existing ->
                        val existingPointsHash = existing.points.sortedBy { it.orderIndex }.map { "${it.latitude},${it.longitude}" }.hashCode()
                        existing.route.name == exportedName && existingPointsHash == exportedPointsHash
                    }

                    if (!isDuplicate) {
                        locationRepository.insertRouteWithPoints(
                            exportedName,
                            exportedRoute.points.mapIndexed { index, ep ->
                                com.example.mockgps.data.model.RoutePoint(
                                    routeId = 0,
                                    orderIndex = index,
                                    latitude = ep.lat,
                                    longitude = ep.lng,
                                    dwellSeconds = ep.dwellSeconds
                                )
                            }
                        )
                        importedRoutes++
                    } else {
                        skippedRoutes++
                    }
                }

                val summaryMsg = "Locations: $importedLocations imported, $skippedLocations skipped.\\nRoutes: $importedRoutes imported, $skippedRoutes skipped."
                onResult(true, summaryMsg)

            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
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
