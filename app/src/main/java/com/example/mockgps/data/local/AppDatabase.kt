package com.example.mockgps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mockgps.data.model.Route
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.SavedLocation

@Database(entities = [SavedLocation::class, Route::class, RoutePoint::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun routeDao(): RouteDao
}
