package com.example.mockgps.data.repository

import com.example.mockgps.data.local.LocationDao
import com.example.mockgps.data.local.RouteDao
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.RouteSummary
import com.example.mockgps.data.model.RouteWithPoints
import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val routeDao: RouteDao
) : LocationRepository {
    override fun getAllLocations(): Flow<List<SavedLocation>> = locationDao.getAllLocations()

    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        showHistory: Boolean,
        showFavorites: Boolean
    ): Flow<List<SavedLocation>> = locationDao.observeSavedLocations(
        query = query,
        sortOption = sortOption,
        showHistory = showHistory,
        showFavorites = showFavorites
    )

    override suspend fun saveLocation(location: SavedLocation) = locationDao.insertLocation(location)
    override suspend fun deleteLocation(location: SavedLocation) = locationDao.deleteLocation(location)
    override suspend fun updateLocation(location: SavedLocation) = locationDao.updateLocation(location)

    override fun observeRoutes(): Flow<List<RouteSummary>> = routeDao.observeRoutes()
    override suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints? = routeDao.getRouteWithPoints(routeId)
    override suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) =
        routeDao.insertRouteWithPoints(name, points)

    override suspend fun deleteRoute(routeId: Int) = routeDao.deleteRoute(routeId)
    override suspend fun updateRouteName(routeId: Int, name: String) = routeDao.updateRouteName(routeId, name)
}
