package com.example.mockgps.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mockgps.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY createdAt DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)
}
