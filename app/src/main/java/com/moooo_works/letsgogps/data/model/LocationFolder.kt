package com.moooo_works.letsgogps.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_folders")
data class LocationFolder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
