package com.moooo_works.letsgogps.data.repository

import com.moooo_works.letsgogps.data.local.LocationDao
import com.moooo_works.letsgogps.data.local.LocationFolderDao
import com.moooo_works.letsgogps.data.local.RouteDao
import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val routeDao: RouteDao,
    private val folderDao: LocationFolderDao
) : LocationRepository {

    override fun getAllLocations(): Flow<List<SavedLocation>> = locationDao.getAllLocations()

    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>> = locationDao.observeSavedLocations(
        query = query,
        sortOption = sortOption,
        filterMode = filterMode,
        folderId = folderId
    )

    override suspend fun saveLocation(location: SavedLocation) = locationDao.insertLocation(location)
    override suspend fun deleteLocation(location: SavedLocation) = locationDao.deleteLocation(location)
    override suspend fun deleteNonFavorites() = locationDao.deleteNonFavorites()
    override suspend fun updateLocation(location: SavedLocation) = locationDao.updateLocation(location)

    override fun observeRoutes(): Flow<List<RouteSummary>> = routeDao.observeRoutes()
    override suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints? = routeDao.getRouteWithPoints(routeId)
    override suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) =
        routeDao.insertRouteWithPoints(name, points)
    override suspend fun deleteRoute(routeId: Int) = routeDao.deleteRoute(routeId)
    override suspend fun updateRouteName(routeId: Int, name: String) = routeDao.updateRouteName(routeId, name)

    override fun observeFolders(): Flow<List<LocationFolder>> = folderDao.observeFolders()
    override fun observeFoldersWithCount(): Flow<List<FolderWithCount>> = folderDao.observeFoldersWithCount()

    override suspend fun createFolder(name: String): Int =
        folderDao.insertFolder(LocationFolder(name = name)).toInt()

    override suspend fun renameFolder(id: Int, name: String) = folderDao.renameFolder(id, name)

    override suspend fun deleteFolder(id: Int) {
        folderDao.clearFolderFromLocations(id)
        folderDao.deleteFolder(id)
    }

    override suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?) =
        folderDao.moveLocationsToFolder(locationIds, folderId)
}
