package com.moooo_works.letsgogps.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = Route::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routeId"])]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeId: Int,
    val orderIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val dwellSeconds: Int = 0
)
