# Folder Categorization + GPX Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GPX import format-selection UI and a full folder categorization system to the Saved Locations screen.

**Architecture:** GPX backend is already implemented in `SettingsViewModel.parseGpxContent()` — Task 1 only adds a format-choice dialog. Folders use Room schema v4→v5 migration, a `LocationFolderDao`, a `LocationFilter` sealed class that replaces the old `showHistory`/`showFavorites` booleans, and new ViewModel/UI state for chip-row filtering, batch-selection, and a dedicated `FolderManagementScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room 2.6.0, Hilt 2.48, MockK + JUnit4 + StandardTestDispatcher

---

## File Map

| Action | File |
|--------|------|
| Modify | `ui/settings/SettingsScreen.kt` — add format-choice dialog for import |
| Modify | `res/values/strings.xml` + `values-en` + `values-ja` + `values-ko` — new strings |
| Create | `data/model/LocationFolder.kt` |
| Create | `data/model/FolderWithCount.kt` |
| Modify | `data/model/SavedLocation.kt` — add `folderId: Int?` |
| Modify | `data/local/AppDatabase.kt` — version 5, MIGRATION_4_5, add LocationFolder entity |
| Create | `data/local/LocationFolderDao.kt` |
| Modify | `di/DatabaseModule.kt` — add MIGRATION_4_5, provide LocationFolderDao |
| Modify | `data/local/LocationDao.kt` — new `observeSavedLocations` signature |
| Create | `ui/savedlocations/LocationFilter.kt` |
| Modify | `domain/repository/LocationRepository.kt` — new filter params + folder methods |
| Modify | `data/repository/LocationRepositoryImpl.kt` — implement folder methods |
| Modify | `ui/savedlocations/SavedLocationsViewModel.kt` — filter/batch/folder state |
| Modify | `ui/savedlocations/SavedLocationsViewModelTest.kt` — update fake + tests |
| Modify | `ui/savedlocations/SavedLocationsScreen.kt` — chip row + batch mode |
| Create | `ui/savedlocations/FolderManagementScreen.kt` |
| Modify | `MainActivity.kt` — add folder_management route |

---

## Task 1: GPX Import Format-Choice Dialog

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

> **Note:** The GPX parsing backend (`parseGpxContent()`) is already fully implemented in `SettingsViewModel.kt`. The file picker already accepts `"*/*"` so GPX files can be selected. This task only makes GPX more discoverable by splitting the import button into a format-choice dialog.

- [ ] **Step 1: Add string resources**

In `values/strings.xml`, add after the existing import strings (around line 183):
```xml
<string name="import_format_dialog_title">選擇匯入格式</string>
<string name="import_format_json">從備份檔匯入（.json）</string>
<string name="import_format_gpx">從 GPX 匯入（.gpx）</string>
```

In `values-en/strings.xml`:
```xml
<string name="import_format_dialog_title">Select Import Format</string>
<string name="import_format_json">Import from backup (.json)</string>
<string name="import_format_gpx">Import from GPX (.gpx)</string>
```

In `values-ja/strings.xml`:
```xml
<string name="import_format_dialog_title">インポート形式を選択</string>
<string name="import_format_json">バックアップからインポート（.json）</string>
<string name="import_format_gpx">GPXからインポート（.gpx）</string>
```

In `values-ko/strings.xml`:
```xml
<string name="import_format_dialog_title">가져오기 형식 선택</string>
<string name="import_format_json">백업 파일 가져오기（.json）</string>
<string name="import_format_gpx">GPX 파일 가져오기（.gpx）</string>
```

- [ ] **Step 2: Add format-choice dialog state to SettingsScreen**

In `SettingsScreen.kt`, find the block of `var show...` state declarations near the top of the `SettingsScreen` composable and add:

```kotlin
var showImportFormatDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Replace import button click to show dialog**

Find the existing import `SettingsMenuItem` (around line 480):
```kotlin
SettingsMenuItem(
    label = stringResource(R.string.settings_import_data),
    subtitle = stringResource(R.string.import_format_hint),
    locked = !isProActive,
    onClick = {
        if (!isProActive) viewModel.requestProUpgrade()
        else importLauncher.launch(arrayOf("application/json", "*/*"))
    }
)
```

Replace with:
```kotlin
SettingsMenuItem(
    label = stringResource(R.string.settings_import_data),
    subtitle = stringResource(R.string.import_format_hint),
    locked = !isProActive,
    onClick = {
        if (!isProActive) viewModel.requestProUpgrade()
        else showImportFormatDialog = true
    }
)
```

- [ ] **Step 4: Add format-choice AlertDialog**

Find where the other dialogs are defined (near the end of the SettingsScreen composable, after the `showClearNonFavoritesDialog` block) and add:

```kotlin
if (showImportFormatDialog) {
    AlertDialog(
        onDismissRequest = { showImportFormatDialog = false },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.import_format_dialog_title)) },
        text = {
            Column {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showImportFormatDialog = false
                        importLauncher.launch(arrayOf("application/json"))
                    }
                ) {
                    Text(
                        stringResource(R.string.import_format_json),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showImportFormatDialog = false
                        importLauncher.launch(arrayOf("*/*"))
                    }
                ) {
                    Text(
                        stringResource(R.string.import_format_gpx),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showImportFormatDialog = false }) {
                Text(stringResource(R.string.map_action_cancel))
            }
        }
    )
}
```

- [ ] **Step 5: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-ko/strings.xml
git commit -m "feat: add GPX format-choice dialog to import menu"
```

---

## Task 2: LocationFolder Entity + SavedLocation.folderId + Migration 4→5

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/data/model/LocationFolder.kt`
- Create: `app/src/main/java/com/moooo_works/letsgogps/data/model/FolderWithCount.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/data/model/SavedLocation.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/di/DatabaseModule.kt`

> No unit tests for this task (pure schema/entity changes). Build verification is the test.

- [ ] **Step 1: Create LocationFolder entity**

Create `data/model/LocationFolder.kt`:
```kotlin
package com.moooo_works.letsgogps.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_folders")
data class LocationFolder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create FolderWithCount projection**

Create `data/model/FolderWithCount.kt`:
```kotlin
package com.moooo_works.letsgogps.data.model

data class FolderWithCount(
    val id: Int,
    val name: String,
    val createdAt: Long,
    val locationCount: Int
)
```

- [ ] **Step 3: Add folderId to SavedLocation**

In `data/model/SavedLocation.kt`, add `folderId` as the last field:
```kotlin
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
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Long = System.currentTimeMillis(),
    val folderId: Int? = null
)
```

- [ ] **Step 4: Add MIGRATION_4_5 and bump AppDatabase to version 5**

In `data/local/AppDatabase.kt`, replace the entire file:
```kotlin
package com.moooo_works.letsgogps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.SavedLocation

@Database(
    entities = [SavedLocation::class, Route::class, RoutePoint::class, LocationFolder::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun routeDao(): RouteDao
    abstract fun locationFolderDao(): LocationFolderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE saved_locations ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE saved_locations ADD COLUMN description TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE saved_locations ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE saved_locations SET sortOrder = createdAt"
                )
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE location_folders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL DEFAULT 0)"
                )
                database.execSQL(
                    "ALTER TABLE saved_locations ADD COLUMN folderId INTEGER"
                )
            }
        }
    }
}
```

- [ ] **Step 5: Add MIGRATION_4_5 and LocationFolderDao to DatabaseModule**

In `di/DatabaseModule.kt`, replace the entire file:
```kotlin
package com.moooo_works.letsgogps.di

import android.content.Context
import androidx.room.Room
import com.moooo_works.letsgogps.data.local.AppDatabase
import com.moooo_works.letsgogps.data.local.LocationDao
import com.moooo_works.letsgogps.data.local.LocationFolderDao
import com.moooo_works.letsgogps.data.local.RouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "mockgps.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()
    }

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideLocationFolderDao(db: AppDatabase): LocationFolderDao = db.locationFolderDao()
}
```

- [ ] **Step 6: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL (Room will compile-check the entities and DAOs)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/data/model/LocationFolder.kt \
        app/src/main/java/com/moooo_works/letsgogps/data/model/FolderWithCount.kt \
        app/src/main/java/com/moooo_works/letsgogps/data/model/SavedLocation.kt \
        app/src/main/java/com/moooo_works/letsgogps/data/local/AppDatabase.kt \
        app/src/main/java/com/moooo_works/letsgogps/di/DatabaseModule.kt
git commit -m "feat: add location_folders table and folderId column (schema v5)"
```

---

## Task 3: LocationFolderDao

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/data/local/LocationFolderDao.kt`

> `AppDatabase.locationFolderDao()` was already declared in Task 2. Build will fail until this DAO is created.

- [ ] **Step 1: Create LocationFolderDao**

Create `data/local/LocationFolderDao.kt`:
```kotlin
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
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/data/local/LocationFolderDao.kt
git commit -m "feat: add LocationFolderDao with CRUD and batch-move queries"
```

---

## Task 4: LocationFilter + Updated LocationDao + LocationRepository + LocationRepositoryImpl

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/LocationFilter.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/data/local/LocationDao.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/domain/repository/LocationRepository.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/data/repository/LocationRepositoryImpl.kt`
- Modify: `app/src/test/java/com/moooo_works/letsgogps/ui/savedlocations/SavedLocationsViewModelTest.kt`

> The `LocationRepository` interface change will break `FakeSavedLocationsRepository` in tests. All files in this task must be done before the build will succeed. Steps 1-5 are all done before running the build.

- [ ] **Step 1: Create LocationFilter sealed class**

Create `ui/savedlocations/LocationFilter.kt`:
```kotlin
package com.moooo_works.letsgogps.ui.savedlocations

sealed class LocationFilter {
    object All : LocationFilter()
    object Favorites : LocationFilter()
    data class Folder(val folderId: Int, val folderName: String) : LocationFilter()

    val filterMode: String
        get() = when (this) {
            is All -> "ALL"
            is Favorites -> "FAVORITES"
            is Folder -> "FOLDER"
        }

    val folderIdOrZero: Int
        get() = if (this is Folder) folderId else 0
}
```

- [ ] **Step 2: Update LocationDao.observeSavedLocations**

Replace the entire `LocationDao.kt`:
```kotlin
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
```

- [ ] **Step 3: Update LocationRepository interface**

Replace `domain/repository/LocationRepository.kt`:
```kotlin
package com.moooo_works.letsgogps.domain.repository

import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getAllLocations(): Flow<List<SavedLocation>>

    fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>>

    suspend fun saveLocation(location: SavedLocation)
    suspend fun deleteLocation(location: SavedLocation)
    suspend fun deleteNonFavorites()
    suspend fun updateLocation(location: SavedLocation)

    fun observeRoutes(): Flow<List<RouteSummary>>
    suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints?
    suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>)
    suspend fun deleteRoute(routeId: Int)
    suspend fun updateRouteName(routeId: Int, name: String)

    // Folder operations
    fun observeFolders(): Flow<List<LocationFolder>>
    fun observeFoldersWithCount(): Flow<List<FolderWithCount>>
    suspend fun createFolder(name: String): Int
    suspend fun renameFolder(id: Int, name: String)
    suspend fun deleteFolder(id: Int)
    suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?)
}
```

- [ ] **Step 4: Update LocationRepositoryImpl**

Replace `data/repository/LocationRepositoryImpl.kt`:
```kotlin
package com.moooo_works.letsgogps.data.repository

import com.moooo_works.letsgogps.data.local.LocationDao
import com.moooo_works.letsgogps.data.local.LocationFolderDao
import com.moooo_works.letsgogps.data.local.RouteDao
import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
    private val routeDao: RouteDao,
    private val folderDao: LocationFolderDao
) : LocationRepository {

    override fun getAllLocations(): Flow<List<SavedLocation>> = locationDao.getAllLocations()

    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>> = locationDao.observeSavedLocations(
        query = query,
        sortOption = sortOption,
        filterMode = filterMode,
        folderId = folderId
    )

    override suspend fun saveLocation(location: SavedLocation) = locationDao.insertLocation(location)
    override suspend fun deleteLocation(location: SavedLocation) = locationDao.deleteLocation(location)
    override suspend fun deleteNonFavorites() = locationDao.deleteNonFavorites()
    override suspend fun updateLocation(location: SavedLocation) = locationDao.updateLocation(location)

    override fun observeRoutes(): Flow<List<RouteSummary>> = routeDao.observeRoutes()
    override suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints? = routeDao.getRouteWithPoints(routeId)
    override suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) =
        routeDao.insertRouteWithPoints(name, points)
    override suspend fun deleteRoute(routeId: Int) = routeDao.deleteRoute(routeId)
    override suspend fun updateRouteName(routeId: Int, name: String) = routeDao.updateRouteName(routeId, name)

    override fun observeFolders(): Flow<List<LocationFolder>> = folderDao.observeFolders()
    override fun observeFoldersWithCount(): Flow<List<FolderWithCount>> = folderDao.observeFoldersWithCount()

    override suspend fun createFolder(name: String): Int =
        folderDao.insertFolder(LocationFolder(name = name)).toInt()

    override suspend fun renameFolder(id: Int, name: String) = folderDao.renameFolder(id, name)

    override suspend fun deleteFolder(id: Int) {
        folderDao.clearFolderFromLocations(id)
        folderDao.deleteFolder(id)
    }

    override suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?) =
        folderDao.moveLocationsToFolder(locationIds, folderId)
}
```

- [ ] **Step 5: Update FakeSavedLocationsRepository and tests in SavedLocationsViewModelTest.kt**

Replace the entire `SavedLocationsViewModelTest.kt`:
```kotlin
package com.moooo_works.letsgogps.ui.savedlocations

import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedLocationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSavedLocationsRepository
    private val proRepository = mockk<ProRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSavedLocationsRepository()
        every { settingsRepository.hasSeenOnboarding() } returns flowOf(true)
        every { settingsRepository.hasSeenSortTip() } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default filter shows all locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "A", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "B", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(2, viewModel.filteredLocations.value.size)
        collectJob.cancel()
    }

    @Test
    fun `favorites filter shows only favorite locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "History", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "Fav", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        viewModel.onFilterChanged(LocationFilter.Favorites)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.filteredLocations.value.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `folder filter shows only locations in that folder`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "In Folder", latitude = 0.0, longitude = 0.0, folderId = 10),
            SavedLocation(id = 2, name = "No Folder", latitude = 0.0, longitude = 0.0, folderId = null)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        viewModel.onFilterChanged(LocationFilter.Folder(folderId = 10, folderName = "Test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.filteredLocations.value.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `deleteLocation calls repository delete`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Test", latitude = 0.0, longitude = 0.0)

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        assertEquals(location, repository.deleted.single())
    }

    @Test
    fun `renameLocation calls repository update with valid name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "New Name")
        advanceUntilIdle()

        assertEquals("New Name", repository.updated.single().name)
    }

    @Test
    fun `renameLocation ignores empty name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "   ")
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `renameLocation ignores too long name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "A".repeat(41))
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `enterBatchSelection activates batch mode with one id selected`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)

        viewModel.enterBatchSelection(locationId = 5)
        advanceUntilIdle()

        assertTrue(viewModel.batchSelection.value.active)
        assertEquals(setOf(5), viewModel.batchSelection.value.selectedIds)
    }

    @Test
    fun `toggleBatchSelection adds and removes ids`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.enterBatchSelection(locationId = 1)
        viewModel.toggleBatchSelection(locationId = 2)
        viewModel.toggleBatchSelection(locationId = 1)
        advanceUntilIdle()

        assertEquals(setOf(2), viewModel.batchSelection.value.selectedIds)
    }

    @Test
    fun `exitBatchSelection clears batch mode`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.enterBatchSelection(locationId = 1)
        viewModel.exitBatchSelection()
        advanceUntilIdle()

        assertFalse(viewModel.batchSelection.value.active)
        assertTrue(viewModel.batchSelection.value.selectedIds.isEmpty())
    }

    @Test
    fun `deleteFolder resets filter to All when current folder is deleted`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.onFilterChanged(LocationFilter.Folder(folderId = 99, folderName = "Old"))
        viewModel.deleteFolder(id = 99)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.filter is LocationFilter.All)
    }
}

private class FakeSavedLocationsRepository : LocationRepository {
    val savedLocations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val deleted = mutableListOf<SavedLocation>()
    val updated = mutableListOf<SavedLocation>()
    val deletedFolderIds = mutableListOf<Int>()
    val movedLocations = mutableListOf<Pair<List<Int>, Int?>>()

    override fun getAllLocations(): Flow<List<SavedLocation>> = savedLocations

    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>> {
        val filtered = savedLocations.value
            .filter { it.name.contains(query, ignoreCase = true) }
            .filter {
                when (filterMode) {
                    "FAVORITES" -> it.isFavorite
                    "FOLDER" -> it.folderId == folderId
                    else -> true
                }
            }
            .let { list ->
                when (sortOption) {
                    SavedLocationsSortOption.NAME_ASC.name -> list.sortedBy { it.name.lowercase() }
                    else -> list.sortedByDescending { it.createdAt }
                }
            }
        return flowOf(filtered)
    }

    override suspend fun saveLocation(location: SavedLocation) = Unit

    override suspend fun deleteLocation(location: SavedLocation) {
        deleted += location
    }

    override suspend fun deleteNonFavorites() {
        savedLocations.value = savedLocations.value.filter { it.isFavorite }
    }

    override suspend fun updateLocation(location: SavedLocation) {
        updated += location
    }

    override fun observeRoutes(): Flow<List<RouteSummary>> = flowOf(emptyList())
    override suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints? = null
    override suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) = Unit
    override suspend fun deleteRoute(routeId: Int) = Unit
    override suspend fun updateRouteName(routeId: Int, name: String) = Unit

    override fun observeFolders(): Flow<List<LocationFolder>> = flowOf(emptyList())
    override fun observeFoldersWithCount(): Flow<List<FolderWithCount>> = flowOf(emptyList())
    override suspend fun createFolder(name: String): Int = 0
    override suspend fun renameFolder(id: Int, name: String) = Unit
    override suspend fun deleteFolder(id: Int) { deletedFolderIds += id }
    override suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?) {
        movedLocations += locationIds to folderId
    }
}
```

- [ ] **Step 6: Build and run tests to verify**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.savedlocations.SavedLocationsViewModelTest
```
Expected: All tests FAIL (ViewModel not yet updated — `onFilterChanged`, `batchSelection`, etc. don't exist yet). This is the expected red state.

- [ ] **Step 7: Commit (even though tests are red — next task makes them green)**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/LocationFilter.kt \
        app/src/main/java/com/moooo_works/letsgogps/data/local/LocationDao.kt \
        app/src/main/java/com/moooo_works/letsgogps/domain/repository/LocationRepository.kt \
        app/src/main/java/com/moooo_works/letsgogps/data/repository/LocationRepositoryImpl.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/savedlocations/SavedLocationsViewModelTest.kt
git commit -m "feat: add LocationFilter and update repository interface for folder filtering"
```

---

## Task 5: SavedLocationsViewModel — Filter + Batch + Folder State

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/SavedLocationsViewModel.kt`

- [ ] **Step 1: Replace the entire SavedLocationsViewModel.kt**

```kotlin
package com.moooo_works.letsgogps.ui.savedlocations

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SavedLocationsSortOption {
    CUSTOM,
    RECENT,
    NAME_ASC
}

data class SavedLocationsUiState(
    val query: String = "",
    val sortOption: SavedLocationsSortOption = SavedLocationsSortOption.CUSTOM,
    val filter: LocationFilter = LocationFilter.All
)

data class BatchSelectionState(
    val active: Boolean = false,
    val selectedIds: Set<Int> = emptySet()
)

@HiltViewModel
class SavedLocationsViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val proRepository: ProRepository,
    private val settingsRepository: com.moooo_works.letsgogps.domain.repository.SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedLocationsUiState())
    val uiState: StateFlow<SavedLocationsUiState> = _uiState.asStateFlow()

    val isProActive: StateFlow<Boolean> = proRepository.isProActive

    val canAddMoreLocations: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val _showProUpgrade = MutableStateFlow(false)
    val showProUpgrade: StateFlow<Boolean> = _showProUpgrade.asStateFlow()

    val showSortTip: StateFlow<Boolean> = combine(
        settingsRepository.hasSeenOnboarding(),
        settingsRepository.hasSeenSortTip()
    ) { onboardingDone, tipSeen ->
        onboardingDone && !tipSeen
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // Folder state
    val folders: StateFlow<List<LocationFolder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val foldersWithCount: StateFlow<List<FolderWithCount>> = repository.observeFoldersWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Batch selection state
    private val _batchSelection = MutableStateFlow(BatchSelectionState())
    val batchSelection: StateFlow<BatchSelectionState> = _batchSelection.asStateFlow()

    val filteredLocations: StateFlow<List<SavedLocation>> = combine(
        _uiState.map { it.query.trim() }.debounce(300),
        _uiState.map { it.sortOption },
        _uiState.map { it.filter }
    ) { query, sort, filter ->
        FilterParams(query, sort, filter)
    }.flatMapLatest { params ->
        repository.observeSavedLocations(
            query = params.query,
            sortOption = params.sortOption.name,
            filterMode = params.filter.filterMode,
            folderId = params.filter.folderIdOrZero
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun dismissSortTip() {
        viewModelScope.launch { settingsRepository.setSortTipSeen() }
    }

    fun dismissProUpgrade() { _showProUpgrade.value = false }
    fun requestProUpgrade() { _showProUpgrade.value = true }

    fun launchBillingFlow(activity: Activity) {
        proRepository.launchBillingFlow(activity)
        dismissProUpgrade()
    }

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onSortOptionChanged(option: SavedLocationsSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun onFilterChanged(filter: LocationFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleFavorite(location: SavedLocation) {
        viewModelScope.launch {
            repository.updateLocation(location.copy(isFavorite = !location.isFavorite))
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch { repository.deleteLocation(location) }
    }

    fun clearNonFavorites() {
        viewModelScope.launch { repository.deleteNonFavorites() }
    }

    fun renameLocation(location: SavedLocation, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isNotEmpty() && trimmedName.length <= 40) {
            viewModelScope.launch {
                repository.updateLocation(location.copy(name = trimmedName))
            }
        }
    }

    fun updateSortOrder(locations: List<SavedLocation>) {
        val baseTime = System.currentTimeMillis()
        viewModelScope.launch {
            locations.forEachIndexed { index, location ->
                val newOrder = baseTime - index
                if (location.sortOrder != newOrder) {
                    repository.updateLocation(location.copy(sortOrder = newOrder))
                }
            }
        }
    }

    // Batch selection
    fun enterBatchSelection(locationId: Int) {
        _batchSelection.value = BatchSelectionState(active = true, selectedIds = setOf(locationId))
    }

    fun toggleBatchSelection(locationId: Int) {
        _batchSelection.update { state ->
            val newIds = if (locationId in state.selectedIds) {
                state.selectedIds - locationId
            } else {
                state.selectedIds + locationId
            }
            state.copy(selectedIds = newIds)
        }
    }

    fun exitBatchSelection() {
        _batchSelection.value = BatchSelectionState()
    }

    fun moveBatchToFolder(folderId: Int?) {
        val ids = _batchSelection.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveLocationsToFolder(ids, folderId)
            exitBatchSelection()
        }
    }

    // Folder management
    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > 30) return
        viewModelScope.launch { repository.createFolder(trimmed) }
    }

    fun renameFolder(id: Int, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > 30) return
        viewModelScope.launch { repository.renameFolder(id, trimmed) }
    }

    fun deleteFolder(id: Int) {
        viewModelScope.launch {
            repository.deleteFolder(id)
            val currentFilter = _uiState.value.filter
            if (currentFilter is LocationFilter.Folder && currentFilter.folderId == id) {
                _uiState.update { it.copy(filter = LocationFilter.All) }
            }
        }
    }
}

private data class FilterParams(
    val query: String,
    val sortOption: SavedLocationsSortOption,
    val filter: LocationFilter
)
```

- [ ] **Step 2: Run tests — they should now pass**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.savedlocations.SavedLocationsViewModelTest
```
Expected: All 10 tests PASS

- [ ] **Step 3: Run full test suite**

```bash
./gradlew test
```
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/SavedLocationsViewModel.kt
git commit -m "feat: replace showHistory/showFavorites with LocationFilter, add batch selection and folder management to SavedLocationsViewModel"
```

---

## Task 6: SavedLocationsScreen — Chip Row + Batch Mode

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/SavedLocationsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add string resources for folder UI**

In `values/strings.xml`, add before the closing `</resources>`:
```xml
<string name="filter_all">全部</string>
<string name="filter_favorites">❤ 最愛</string>
<string name="folder_manage_button">管理</string>
<string name="folder_move_to_title">移到資料夾</string>
<string name="folder_uncategorized">未分類</string>
<string name="batch_move_to_folder">移到資料夾 (%1$d)</string>
<string name="batch_cancel">取消選取</string>
```

In `values-en/strings.xml`:
```xml
<string name="filter_all">All</string>
<string name="filter_favorites">❤ Favorites</string>
<string name="folder_manage_button">Manage</string>
<string name="folder_move_to_title">Move to Folder</string>
<string name="folder_uncategorized">Uncategorized</string>
<string name="batch_move_to_folder">Move to Folder (%1$d)</string>
<string name="batch_cancel">Cancel</string>
```

In `values-ja/strings.xml`:
```xml
<string name="filter_all">すべて</string>
<string name="filter_favorites">❤ お気に入り</string>
<string name="folder_manage_button">管理</string>
<string name="folder_move_to_title">フォルダーに移動</string>
<string name="folder_uncategorized">未分類</string>
<string name="batch_move_to_folder">フォルダーに移動 (%1$d)</string>
<string name="batch_cancel">選択解除</string>
```

In `values-ko/strings.xml`:
```xml
<string name="filter_all">전체</string>
<string name="filter_favorites">❤ 즐겨찾기</string>
<string name="folder_manage_button">관리</string>
<string name="folder_move_to_title">폴더로 이동</string>
<string name="folder_uncategorized">미분류</string>
<string name="batch_move_to_folder">폴더로 이동 (%1$d)</string>
<string name="batch_cancel">선택 취소</string>
```

- [ ] **Step 2: Add required imports to SavedLocationsScreen.kt**

At the top of `SavedLocationsScreen.kt`, add these imports (after existing ones):
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.moooo_works.letsgogps.data.model.LocationFolder
```

- [ ] **Step 3: Add new state variables at the top of SavedLocationsScreen composable**

After the existing state declarations (`var locationToDelete`, `var locationToRename`, etc.), add:
```kotlin
val folders by viewModel.folders.collectAsStateWithLifecycle()
val batchSelection by viewModel.batchSelection.collectAsStateWithLifecycle()
var showFolderPickerDialog by remember { mutableStateOf(false) }
```

- [ ] **Step 4: Replace the TopAppBar "管理" button and add chip row**

In `SavedLocationsScreen.kt`, find the `TopAppBar` composable that shows "儲存位置". Add a "管理" action button to it:

```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.nav_saved_locations), ...) },
    actions = {
        // existing sort and overflow menu buttons ...
        TextButton(onClick = { navController.navigate("folder_management") }) {
            Text(stringResource(R.string.folder_manage_button))
        }
    },
    ...
)
```

> **Note:** `SavedLocationsScreen` currently receives `onNavigateBack` and `onLocationSelected` callbacks. To navigate to folder management, add a new callback parameter `onNavigateToFolderManagement: () -> Unit` to the function signature, and call it from the "管理" button. Wire it up in `MainActivity.kt` (Task 7).

Add this parameter to the function signature:
```kotlin
@Composable
fun SavedLocationsScreen(
    onNavigateBack: () -> Unit,
    onLocationSelected: (Double, Double) -> Unit,
    onNavigateToFolderManagement: () -> Unit = {},
    viewModel: SavedLocationsViewModel
) {
```

Then the manage button:
```kotlin
TextButton(onClick = onNavigateToFolderManagement) {
    Text(stringResource(R.string.folder_manage_button))
}
```

- [ ] **Step 5: Add chip row below the search field**

After the search `OutlinedTextField` and before the existing sort-tip `Card`, add:

```kotlin
LazyRow(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    item {
        FilterChip(
            selected = uiState.filter is LocationFilter.All,
            onClick = { viewModel.onFilterChanged(LocationFilter.All) },
            label = { Text(stringResource(R.string.filter_all)) }
        )
    }
    item {
        FilterChip(
            selected = uiState.filter is LocationFilter.Favorites,
            onClick = { viewModel.onFilterChanged(LocationFilter.Favorites) },
            label = { Text(stringResource(R.string.filter_favorites)) }
        )
    }
    items(folders) { folder ->
        FilterChip(
            selected = uiState.filter.let { it is LocationFilter.Folder && it.folderId == folder.id },
            onClick = { viewModel.onFilterChanged(LocationFilter.Folder(folder.id, folder.name)) },
            label = { Text(folder.name) }
        )
    }
}
```

Remove the existing "最愛/歷史" toggle buttons (the `Row` with `FilterChip` for `showHistory`/`showFavorites`) — they are replaced by the new chip row above.

- [ ] **Step 6: Add batch-mode bottom bar to the Scaffold**

In the `Scaffold` composable in `SavedLocationsScreen`, add a `bottomBar` parameter:

```kotlin
Scaffold(
    topBar = { /* existing */ },
    bottomBar = {
        if (batchSelection.active) {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                TextButton(
                    onClick = { showFolderPickerDialog = true },
                    enabled = batchSelection.selectedIds.isNotEmpty()
                ) {
                    Text(
                        stringResource(R.string.batch_move_to_folder, batchSelection.selectedIds.size),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.exitBatchSelection() }) {
                    Text(stringResource(R.string.batch_cancel))
                }
            }
        }
    }
) { paddingValues -> /* existing content */ }
```

- [ ] **Step 7: Add long-press and checkbox to each location list item**

Find the `ReorderableItem` composable that wraps each location item. Add `combinedClickable` to the item's root `Card` or `Row` modifier:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
// On the item Card/Row modifier, replace clickable with:
.combinedClickable(
    onClick = {
        if (batchSelection.active) {
            viewModel.toggleBatchSelection(location.id)
        } else {
            onLocationSelected(location.latitude, location.longitude)
        }
    },
    onLongClick = {
        if (!batchSelection.active) {
            viewModel.enterBatchSelection(location.id)
        }
    }
)
```

At the start of each list item row, add a checkbox when batch mode is active:

```kotlin
if (batchSelection.active) {
    Checkbox(
        checked = location.id in batchSelection.selectedIds,
        onCheckedChange = { viewModel.toggleBatchSelection(location.id) },
        modifier = Modifier.padding(end = 4.dp)
    )
}
```

- [ ] **Step 8: Add folder-picker dialog**

After the existing dialogs (near the end of the composable), add:

```kotlin
if (showFolderPickerDialog) {
    var selectedFolderId by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = { showFolderPickerDialog = false },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.folder_move_to_title)) },
        text = {
            Column {
                // "未分類" option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedFolderId = null }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFolderId == null,
                        onClick = { selectedFolderId = null }
                    )
                    Text(
                        stringResource(R.string.folder_uncategorized),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolderId = folder.id }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFolderId == folder.id,
                            onClick = { selectedFolderId = folder.id }
                        )
                        Text(
                            folder.name,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.moveBatchToFolder(selectedFolderId)
                showFolderPickerDialog = false
            }) {
                Text(stringResource(R.string.map_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { showFolderPickerDialog = false }) {
                Text(stringResource(R.string.map_action_cancel))
            }
        }
    )
}
```

> **Note:** `R.string.map_action_confirm` may not exist yet. Check `strings.xml` — if not present, add `<string name="map_action_confirm">確認</string>` (EN: `Confirm`, JA: `確認`, KO: `확인`).

- [ ] **Step 9: Build and verify**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/SavedLocationsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-ko/strings.xml
git commit -m "feat: add folder chip row, batch selection mode, and folder picker dialog to SavedLocationsScreen"
```

---

## Task 7: FolderManagementScreen + MainActivity Routing

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/FolderManagementScreen.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml` + 3 locales

- [ ] **Step 1: Add folder management strings**

In `values/strings.xml`, add:
```xml
<string name="folder_management_title">管理資料夾</string>
<string name="folder_new">＋ 新增資料夾</string>
<string name="folder_name_hint">資料夾名稱</string>
<string name="folder_rename_title">重新命名資料夾</string>
<string name="folder_delete_title">刪除資料夾</string>
<string name="folder_delete_msg">資料夾內的位置將移至未分類，位置本身不受影響。</string>
<string name="folder_location_count">%1$d 個位置</string>
```

In `values-en/strings.xml`:
```xml
<string name="folder_management_title">Manage Folders</string>
<string name="folder_new">＋ New Folder</string>
<string name="folder_name_hint">Folder name</string>
<string name="folder_rename_title">Rename Folder</string>
<string name="folder_delete_title">Delete Folder</string>
<string name="folder_delete_msg">Locations in this folder will be moved to Uncategorized. The locations themselves will not be affected.</string>
<string name="folder_location_count">%1$d locations</string>
```

In `values-ja/strings.xml`:
```xml
<string name="folder_management_title">フォルダー管理</string>
<string name="folder_new">＋ 新規フォルダー</string>
<string name="folder_name_hint">フォルダー名</string>
<string name="folder_rename_title">名前を変更</string>
<string name="folder_delete_title">フォルダーを削除</string>
<string name="folder_delete_msg">フォルダー内の場所は「未分類」に移動されます。場所自体は削除されません。</string>
<string name="folder_location_count">%1$d 件</string>
```

In `values-ko/strings.xml`:
```xml
<string name="folder_management_title">폴더 관리</string>
<string name="folder_new">＋ 새 폴더</string>
<string name="folder_name_hint">폴더 이름</string>
<string name="folder_rename_title">폴더 이름 변경</string>
<string name="folder_delete_title">폴더 삭제</string>
<string name="folder_delete_msg">폴더 안의 위치는 미분류로 이동됩니다. 위치 자체는 삭제되지 않습니다.</string>
<string name="folder_location_count">%1$d개</string>
```

- [ ] **Step 2: Create FolderManagementScreen.kt**

Create `ui/savedlocations/FolderManagementScreen.kt`:
```kotlin
package com.moooo_works.letsgogps.ui.savedlocations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.model.FolderWithCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: SavedLocationsViewModel
) {
    val foldersWithCount by viewModel.foldersWithCount.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderWithCount?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderWithCount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folder_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { showCreateDialog = true }) {
                    Text(stringResource(R.string.folder_new))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(foldersWithCount, key = { it.id }) { folder ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.folder_location_count, folder.locationCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { folderToRename = folder }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.folder_rename_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { folderToDelete = folder }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.folder_delete_title),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Create folder dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.folder_new)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 30) newName = it },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(newName)
                        showCreateDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(R.string.map_action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.map_action_cancel))
                }
            }
        )
    }

    // Rename folder dialog
    folderToRename?.let { folder ->
        var editedName by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.folder_rename_title)) },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { if (it.length <= 30) editedName = it },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameFolder(folder.id, editedName)
                        folderToRename = null
                    },
                    enabled = editedName.isNotBlank()
                ) {
                    Text(stringResource(R.string.map_action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.map_action_cancel))
                }
            }
        )
    }

    // Delete folder confirmation dialog
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.folder_delete_title)) },
            text = { Text(stringResource(R.string.folder_delete_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.id)
                        folderToDelete = null
                    }
                ) {
                    Text(
                        stringResource(R.string.map_search_clear),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.map_action_cancel))
                }
            }
        )
    }
}
```

- [ ] **Step 3: Update MainActivity.kt**

In `MainActivity.kt`, inside the `NavHost`:

1. Hide the bottom bar on `folder_management` route. Find where `bottomBar` is defined (inside the outer `Scaffold`) and gate it:
```kotlin
bottomBar = {
    val showBottomBar = currentRoute != "folder_management"
    if (showBottomBar) {
        NavigationBar(...) { /* existing items */ }
    }
},
```

2. Inside `composable("saved_locations")`, pass the new callback:
```kotlin
composable("saved_locations") { backStackEntry ->
    val viewModel: SavedLocationsViewModel = hiltViewModel(backStackEntry)
    SavedLocationsScreen(
        viewModel = viewModel,
        onNavigateBack = { },
        onNavigateToFolderManagement = { navController.navigate("folder_management") },
        onLocationSelected = { lat, lng ->
            // existing code ...
        }
    )
}
```

3. Add the new route at the end of the `NavHost` block (before the closing `}`):
```kotlin
composable("folder_management") {
    val viewModel: SavedLocationsViewModel = hiltViewModel(
        navController.getBackStackEntry("saved_locations")
    )
    FolderManagementScreen(
        onNavigateBack = { navController.popBackStack() },
        viewModel = viewModel
    )
}
```

Also add the import at the top of `MainActivity.kt`:
```kotlin
import com.moooo_works.letsgogps.ui.savedlocations.FolderManagementScreen
```

- [ ] **Step 4: Build and run all tests**

```bash
./gradlew assembleDebug && ./gradlew test
```
Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/savedlocations/FolderManagementScreen.kt \
        app/src/main/java/com/moooo_works/letsgogps/MainActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/res/values-ja/strings.xml \
        app/src/main/res/values-ko/strings.xml
git commit -m "feat: add FolderManagementScreen and folder_management route in MainActivity"
```

---

## Self-Review Checklist

**Spec coverage:**

| Spec requirement | Covered in task |
|-----------------|-----------------|
| GPX 匯入觸發入口（設定頁匯入按鈕） | Task 1 |
| Waypoints → SavedLocation | Already implemented |
| Tracks/Routes → Route + RoutePoint | Already implemented |
| 匯入完成 Snackbar | Already implemented (existing `onResult` handler) |
| location_folders 資料表 | Task 2 |
| saved_locations.folderId 欄位 | Task 2 |
| MIGRATION_4_5 | Task 2 |
| LocationFolderDao CRUD | Task 3 |
| 單列 Chip 篩選列（全部、最愛、資料夾） | Task 6 |
| 批次選取模式（長按進入、checkbox、bottom action bar） | Task 6 |
| 移到資料夾 Dialog（含「未分類」選項） | Task 6 |
| 資料夾管理頁（列表、重新命名、刪除、新增） | Task 7 |
| 刪除資料夾後位置歸入未分類 | Task 3 (clearFolderFromLocations) + Task 5 (deleteFolder) |
| TopBar「管理」按鈕 | Task 6 (onNavigateToFolderManagement) |
| 刪除資料夾時切換篩選回「全部」 | Task 5 (deleteFolder in ViewModel) |
| 現有最愛/歷史 toggle 移除，由 chip 取代 | Task 6 |

**Placeholder scan:** No TBDs or placeholders found.

**Type consistency:**
- `LocationFilter.filterMode` returns `"ALL"` / `"FAVORITES"` / `"FOLDER"` — matches `LocationDao` SQL `:filterMode` checks ✓
- `LocationFilter.folderIdOrZero` — matches `LocationDao` `:folderId` parameter ✓
- `FolderWithCount` fields: `id`, `name`, `createdAt`, `locationCount` — matches `LocationFolderDao` JOIN query columns ✓
- `FakeSavedLocationsRepository.observeSavedLocations` new signature: `filterMode: String, folderId: Int` — matches `LocationRepository` interface ✓
- `SavedLocationsScreen` new param `onNavigateToFolderManagement: () -> Unit` — wired in `MainActivity.kt` ✓
- `viewModel.deleteFolder(id = 99)` in test — matches `SavedLocationsViewModel.deleteFolder(id: Int)` ✓

**Scope:** Each task is independently buildable except Tasks 4-5 which form a single interface-change pair. Noted in Task 4 header.
