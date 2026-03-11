package com.moooo_works.letsgogps.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val defaultSpeed: Double = 5.0, // km/h
    val transportMode: String = "WALKING",
    val createdAt: Long = System.currentTimeMillis()
)
