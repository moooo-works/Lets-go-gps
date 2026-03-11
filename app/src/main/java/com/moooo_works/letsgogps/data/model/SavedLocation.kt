package com.moooo_works.letsgogps.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean = false,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
