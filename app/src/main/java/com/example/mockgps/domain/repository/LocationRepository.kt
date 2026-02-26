package com.example.mockgps.domain.repository

import com.example.mockgps.data.model.Route
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getAllLocations(): Flow<List<SavedLocation>>
    suspend fun saveLocation(location: SavedLocation)
    suspend fun deleteLocation(location: SavedLocation)
    suspend fun updateLocation(location: SavedLocation)

    fun getAllRoutes(): Flow<List<Route>>
    suspend fun createRoute(route: Route, points: List<RoutePoint>)
    suspend fun deleteRoute(route: Route)
    fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>>
}
