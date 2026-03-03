package com.example.mockgps.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.mockgps.data.model.Route
import com.example.mockgps.data.model.RoutePoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun getAllRoutes(): Flow<List<Route>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: Route): Long

    @Delete
    suspend fun deleteRoute(route: Route)
    @androidx.room.Update
    suspend fun updateRoute(route: Route)

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY orderIndex ASC")
    fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: RoutePoint)

    @Delete
    suspend fun deletePoint(point: RoutePoint)

    @Transaction
    suspend fun createRouteWithPoints(route: Route, points: List<RoutePoint>) {
        val routeId = insertRoute(route)
        points.forEach {
             insertPoint(it.copy(routeId = routeId.toInt()))
        }
    }
}
