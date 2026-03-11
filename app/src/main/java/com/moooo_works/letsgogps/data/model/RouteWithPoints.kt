package com.moooo_works.letsgogps.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class RouteWithPoints(
    @Embedded val route: Route,
    @Relation(
        parentColumn = "id",
        entityColumn = "routeId"
    )
    val points: List<RoutePoint>
)
