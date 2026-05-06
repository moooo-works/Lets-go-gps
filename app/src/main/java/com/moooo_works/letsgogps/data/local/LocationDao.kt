package com.moooo_works.letsgogps.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moooo_works.letsgogps.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY createdAt DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("""
        SELECT * FROM saved_locations
        WHERE name LIKE '%' || :query || '%'
          AND (
            :filterMode = 'ALL'
            OR (:filterMode = 'FAVORITES' AND isFavorite = 1)
            OR (:filterMode = 'FOLDER' AND folderId = :folderId)
          )
        ORDER BY
            CASE WHEN :sortOption = 'NAME_ASC' THEN name END COLLATE NOCASE ASC,
            CASE WHEN :sortOption = 'RECENT' THEN createdAt END DESC,
            CASE WHEN :sortOption = 'CUSTOM' THEN sortOrder END DESC,
            id DESC
        """)
    fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    @Query("DELETE FROM saved_locations WHERE isFavorite = 0")
    suspend fun deleteNonFavorites()

    @Update
    suspend fun updateLocation(location: SavedLocation)
}
