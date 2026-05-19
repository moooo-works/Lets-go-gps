package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocationPinController(
    private val state: MutableStateFlow<MapUiState>,
    private val scope: CoroutineScope,
    private val repository: LocationRepository
) {
    fun select(location: SavedLocation) {
        state.update { it.copy(selectedLocation = location) }
    }

    fun dismiss() {
        state.update { it.copy(selectedLocation = null, showEditLocationDialog = false) }
    }

    fun showEditDialog() {
        state.update { it.copy(showEditLocationDialog = true) }
    }

    fun dismissEditDialog() {
        state.update { it.copy(showEditLocationDialog = false) }
    }

    fun delete() {
        val location = state.value.selectedLocation ?: return
        scope.launch {
            repository.deleteLocation(location)
            dismiss()
        }
    }

    fun toggleFavorite() {
        val location = state.value.selectedLocation ?: return
        val updated = location.copy(isFavorite = !location.isFavorite)
        scope.launch {
            repository.updateLocation(updated)
            state.update { it.copy(selectedLocation = updated) }
        }
    }

    fun updateDetails(name: String, description: String) {
        val location = state.value.selectedLocation ?: return
        val updated = location.copy(name = name.trim(), description = description.trim())
        scope.launch {
            repository.updateLocation(updated)
            state.update { it.copy(selectedLocation = updated, showEditLocationDialog = false) }
        }
    }

    internal fun saveIfNeeded(latLng: LatLng) {
        scope.launch {
            val epsilon = 0.0001
            val exists = state.value.savedLocations.any {
                kotlin.math.abs(it.latitude - latLng.latitude) < epsilon &&
                    kotlin.math.abs(it.longitude - latLng.longitude) < epsilon
            }
            if (!exists) {
                repository.saveLocation(
                    SavedLocation(
                        name = "Saved ${System.currentTimeMillis()}",
                        latitude = latLng.latitude,
                        longitude = latLng.longitude
                    )
                )
            }
        }
    }
}
