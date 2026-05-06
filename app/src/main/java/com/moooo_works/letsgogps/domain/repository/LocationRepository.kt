package com.moooo_works.letsgogps.domain.repository

import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getAllLocations(): Flow<List<SavedLocation>>

    fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>>

    suspend fun saveLocation(location: SavedLocation)
    suspend fun deleteLocation(location: SavedLocation)
    suspend fun deleteNonFavorites()
    suspend fun updateLocation(location: SavedLocation)

    fun observeRoutes(): Flow<List<RouteSummary>>
    suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints?
    suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>)
    suspend fun deleteRoute(routeId: Int)
    suspend fun updateRouteName(routeId: Int, name: String)

    // Folder operations
    fun observeFolders(): Flow<List<LocationFolder>>
    fun observeFoldersWithCount(): Flow<List<FolderWithCount>>
    suspend fun createFolder(name: String): Int
    suspend fun renameFolder(id: Int, name: String)
    suspend fun deleteFolder(id: Int)
    suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?)
}
