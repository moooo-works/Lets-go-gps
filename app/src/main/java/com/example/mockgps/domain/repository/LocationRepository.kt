package com.example.mockgps.domain.repository

import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.RouteSummary
import com.example.mockgps.data.model.RouteWithPoints
import com.example.mockgps.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getAllLocations(): Flow<List<SavedLocation>>
    suspend fun saveLocation(location: SavedLocation)
    suspend fun deleteLocation(location: SavedLocation)
    suspend fun updateLocation(location: SavedLocation)

    fun observeRoutes(): Flow<List<RouteSummary>>
    suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints?
    suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>)
    suspend fun deleteRoute(routeId: Int)
    suspend fun updateRouteName(routeId: Int, name: String)
}
