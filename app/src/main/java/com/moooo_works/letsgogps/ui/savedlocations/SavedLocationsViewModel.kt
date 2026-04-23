package com.moooo_works.letsgogps.ui.savedlocations

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val showHistory: Boolean = true,
    val showFavorites: Boolean = true
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

    fun dismissSortTip() {
        viewModelScope.launch {
            settingsRepository.setSortTipSeen()
        }
    }

    fun dismissProUpgrade() { _showProUpgrade.value = false }

    fun requestProUpgrade() { _showProUpgrade.value = true }

    fun launchBillingFlow(activity: Activity) {
        proRepository.launchBillingFlow(activity)
        dismissProUpgrade()
    }

    val filteredLocations: StateFlow<List<SavedLocation>> = combine(
        _uiState.map { it.query.trim() }.debounce(300),
        _uiState.map { it.sortOption },
        _uiState.map { it.showHistory },
        _uiState.map { it.showFavorites }
    ) { query, sort, showHistory, showFavorites ->
        QueryParams(query, sort, showHistory, showFavorites)
    }.flatMapLatest { params ->
        repository.observeSavedLocations(
            query = params.query,
            sortOption = params.sortOption.name,
            showHistory = params.showHistory,
            showFavorites = params.showFavorites
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onSortOptionChanged(option: SavedLocationsSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun onShowHistoryChanged(checked: Boolean) {
        _uiState.update { current ->
            if (!checked && !current.showFavorites) current else current.copy(showHistory = checked)
        }
    }

    fun onShowFavoritesChanged(checked: Boolean) {
        _uiState.update { current ->
            if (!checked && !current.showHistory) current else current.copy(showFavorites = checked)
        }
    }

    fun toggleFavorite(location: SavedLocation) {
        viewModelScope.launch {
            repository.updateLocation(location.copy(isFavorite = !location.isFavorite))
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.deleteLocation(location)
        }
    }

    fun clearNonFavorites() {
        viewModelScope.launch {
            repository.deleteNonFavorites()
        }
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
}

private data class QueryParams(
    val query: String,
    val sortOption: SavedLocationsSortOption,
    val showHistory: Boolean,
    val showFavorites: Boolean
)

