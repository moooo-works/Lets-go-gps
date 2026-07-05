package com.moooo_works.letsgogps.data.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.utils.GeoDistanceMeters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton
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
    val format: String = "JSON",
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

/**
 * Shared import/export engine for JSON backups, GPX files and plain
 * "lat,lng" text files. Extracted from SettingsViewModel so the saved
 * locations and routes pages can offer the same functionality. All error
 * paths throw with a localized, user-facing message; Pro gating stays in
 * the calling ViewModels.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToUri(uri: Uri, includeSavedLocations: Boolean, includeRoutes: Boolean) {
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
    }

    /** Parses the file behind [uri] and returns a preview. Throws with a
     *  localized message on any unrecognized or malformed content. */
    suspend fun parseImport(uri: Uri): ImportPreview {
        val fileContent = readContent(uri)

        if (fileContent.isBlank()) {
            throw IllegalArgumentException(context.getString(R.string.import_error_empty))
        }

        val trimmedContent = fileContent.trimStart()

        if (trimmedContent.startsWith("<")) {
            val exportData = try {
                parseGpxContent(fileContent)
            } catch (e: Exception) {
                throw IllegalArgumentException(context.getString(R.string.import_error_invalid_gpx, e.message.orEmpty()))
            }
            return ImportPreview(
                uri = uri,
                schemaVersion = 0,
                savedLocationsCount = exportData.savedLocations.size,
                routesCount = exportData.routes.size,
                isGpx = true,
                format = "GPX"
            )
        }

        if (!trimmedContent.startsWith("{") && !trimmedContent.startsWith("[")) {
            // Not XML, not JSON — some apps export plain "lat,lng" lines
            // under a .gpx/.txt extension.
            val plainData = parsePlainCoordinatesContent(fileContent)
                ?: throw IllegalArgumentException(context.getString(R.string.import_error_unsupported_format))
            return ImportPreview(
                uri = uri,
                schemaVersion = 0,
                savedLocationsCount = plainData.savedLocations.size,
                routesCount = plainData.routes.size,
                format = "TXT"
            )
        }

        val exportData = try {
            gson.fromJson(fileContent, ExportData::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException(context.getString(R.string.import_error_invalid_json, e.message.orEmpty()))
        } ?: throw IllegalArgumentException(context.getString(R.string.import_error_invalid_data))

        // A foreign JSON schema (e.g. another app's backup) deserializes into
        // ExportData's defaults without error, yielding a useless 0/0 preview.
        // Reject unless at least one of our top-level keys is actually present
        // ("settings" excluded: foreign backups can have a same-named object).
        val rootKeys = try {
            gson.fromJson(fileContent, com.google.gson.JsonObject::class.java)?.keySet().orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        if (listOf("schemaVersion", "savedLocations", "routes", "folders").none { it in rootKeys }) {
            throw IllegalArgumentException(context.getString(R.string.import_error_foreign_backup))
        }
        if (exportData.schemaVersion > 3) {
            throw IllegalArgumentException(context.getString(R.string.import_error_schema_version, exportData.schemaVersion))
        }

        return ImportPreview(
            uri = uri,
            schemaVersion = exportData.schemaVersion,
            savedLocationsCount = exportData.savedLocations?.size ?: 0,
            routesCount = exportData.routes?.size ?: 0,
            foldersCount = exportData.folders?.size ?: 0,
            hasSettings = exportData.settings != null,
        )
    }

    /** Re-reads and applies the file behind [preview]. Returns a localized
     *  summary message. */
    suspend fun applyImport(preview: ImportPreview): String {
        val fileContent = readContent(preview.uri)

        val trimmedContent = fileContent.trimStart()
        val exportData = when {
            trimmedContent.startsWith("<") -> parseGpxContent(fileContent)
            trimmedContent.startsWith("{") || trimmedContent.startsWith("[") ->
                gson.fromJson(fileContent, ExportData::class.java)
            else -> parsePlainCoordinatesContent(fileContent)
                ?: throw IllegalArgumentException(context.getString(R.string.import_error_unsupported_format))
        }

        var importedLocations = 0
        var skippedLocations = 0
        var importedRoutes = 0
        var skippedRoutes = 0

        // Folders first — build a remap from exported id → newly-created/found id.
        // Same-name folders are merged (case-sensitive trim) so re-importing
        // the same backup doesn't proliferate duplicates.
        //
        // Gson ignores Kotlin's `= emptyList()` default when a class lacks a
        // no-arg constructor; a missing "folders"/"savedLocations" key in
        // legacy v1/v2 backups can deserialize to null. Coalesce defensively.
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
                    insertRoute(newName, exportedPoints)
                    importedRoutes++
                }
            } else {
                insertRoute(exportedName, exportedPoints)
                importedRoutes++
            }
        }

        exportData.settings?.let { applySettings(it) }

        return context.getString(R.string.import_result_locations, importedLocations, skippedLocations) + "\n" +
            context.getString(R.string.import_result_routes, importedRoutes, skippedRoutes)
    }

    private suspend fun readContent(uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("openInputStream returned null")
        inputStream.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
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

    /**
     * Parses plain-text coordinate files: one "lat,lng" pair per line
     * (the export format of some other mock-GPS apps, often with a .gpx
     * extension despite not being XML). Returns null unless every
     * non-blank line is a valid coordinate pair. A single point becomes a
     * saved location; two or more become one route.
     */
    private fun parsePlainCoordinatesContent(content: String): ExportData? {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val points = lines.map { line ->
            val parts = line.split(',')
            if (parts.size != 2) return null
            val lat = parts[0].trim().toDoubleOrNull() ?: return null
            val lng = parts[1].trim().toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
            ExportRoutePoint(lat = lat, lng = lng)
        }
        return if (points.size == 1) {
            ExportData(
                schemaVersion = 0,
                savedLocations = listOf(
                    ExportSavedLocation(
                        name = context.getString(R.string.import_default_point_name),
                        lat = points[0].lat,
                        lng = points[0].lng
                    )
                ),
                routes = emptyList()
            )
        } else {
            ExportData(
                schemaVersion = 0,
                savedLocations = emptyList(),
                routes = listOf(ExportRoute(name = context.getString(R.string.import_default_route_name), points = points))
            )
        }
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
                        name = waypoint.directChildText("name").ifBlank {
                            context.getString(R.string.import_default_gpx_waypoint_name, waypoints.size + 1)
                        },
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
                        name = track.directChildText("name").ifBlank {
                            context.getString(R.string.import_default_gpx_track_name, routes.size + 1)
                        },
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
                        name = route.directChildText("name").ifBlank {
                            context.getString(R.string.import_default_gpx_route_name, routes.size + 1)
                        },
                        points = points
                    )
                )
            }
        }

        return ExportData(schemaVersion = 0, savedLocations = waypoints, routes = routes)
    }

    private suspend fun insertRoute(name: String, points: List<ExportRoutePoint>) {
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
}
