package com.moooo_works.letsgogps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationFolderDao {

    @Query("SELECT * FROM location_folders ORDER BY createdAt ASC")
    fun observeFolders(): Flow<List<LocationFolder>>

    @Query("""
        SELECT lf.id, lf.name, lf.createdAt, COUNT(sl.id) AS locationCount
        FROM location_folders lf
        LEFT JOIN saved_locations sl ON sl.folderId = lf.id
        GROUP BY lf.id
        ORDER BY lf.createdAt ASC
    """)
    fun observeFoldersWithCount(): Flow<List<FolderWithCount>>

    @Insert
    suspend fun insertFolder(folder: LocationFolder): Long

    @Query("UPDATE location_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Int, name: String)

    @Query("UPDATE saved_locations SET folderId = NULL WHERE folderId = :id")
    suspend fun clearFolderFromLocations(id: Int)

    @Query("DELETE FROM location_folders WHERE id = :id")
    suspend fun deleteFolder(id: Int)

    @Query("UPDATE saved_locations SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun moveLocationsToFolder(ids: List<Int>, folderId: Int?)
}
