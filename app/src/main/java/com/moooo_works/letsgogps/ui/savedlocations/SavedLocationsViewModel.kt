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
            _uiState.update { current ->
                if (current.filter is LocationFilter.Folder && current.filter.folderId == id) {
                    current.copy(filter = LocationFilter.All)
                } else current
            }
        }
    }
}

private data class FilterParams(
    val query: String,
    val sortOption: SavedLocationsSortOption,
    val filter: LocationFilter
)
