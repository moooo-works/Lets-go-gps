package com.example.mockgps.data.repository

import com.example.mockgps.data.local.LocationDao
import com.example.mockgps.data.local.RouteDao
import com.example.mockgps.data.model.Route
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val routeDao: RouteDao
) : LocationRepository {
    override fun getAllLocations(): Flow<List<SavedLocation>> = locationDao.getAllLocations()
    override suspend fun saveLocation(location: SavedLocation) = locationDao.insertLocation(location)
    override suspend fun deleteLocation(location: SavedLocation) = locationDao.deleteLocation(location)
    override suspend fun updateLocation(location: SavedLocation) = locationDao.updateLocation(location)

    override fun getAllRoutes(): Flow<List<Route>> = routeDao.getAllRoutes()
    override suspend fun createRoute(route: Route, points: List<RoutePoint>) = routeDao.createRouteWithPoints(route, points)
    override suspend fun deleteRoute(route: Route) = routeDao.deleteRoute(route)
    override fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>> = routeDao.getPointsForRoute(routeId)
}
