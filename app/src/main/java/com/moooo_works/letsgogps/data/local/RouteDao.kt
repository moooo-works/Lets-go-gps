package com.moooo_works.letsgogps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query(
        """
        SELECT routes.id, routes.name, COUNT(route_points.id) AS pointCount, routes.createdAt
        FROM routes
        LEFT JOIN route_points ON routes.id = route_points.routeId
        GROUP BY routes.id
        ORDER BY routes.createdAt DESC
        """
    )
    fun observeRoutes(): Flow<List<RouteSummary>>

    @Transaction
    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: Route): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<RoutePoint>)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRoute(routeId: Int)

    @Query("UPDATE routes SET name = :name WHERE id = :routeId")
    suspend fun updateRouteName(routeId: Int, name: String)

    @Transaction
    suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) {
        val routeId = insertRoute(Route(name = name)).toInt()
        val routePoints = points.mapIndexed { index, point ->
            point.copy(id = 0, routeId = routeId, orderIndex = index)
        }
        insertPoints(routePoints)
    }
}
