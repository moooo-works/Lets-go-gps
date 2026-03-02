package com.example.mockgps.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mockgps.data.model.Route
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class RoutesUiState(
    val routes: List<Route> = emptyList()
)

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutesUiState())
    val uiState: StateFlow<RoutesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllRoutes().collect { items ->
                _uiState.update { it.copy(routes = items) }
            }
        }
    }

    fun deleteRoute(route: Route) {
        viewModelScope.launch {
            repository.deleteRoute(route)
        }
    }

    fun renameRoute(route: Route, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isNotEmpty() && trimmedName.length <= 40) {
            viewModelScope.launch {
                repository.updateRoute(route.copy(name = trimmedName))
            }
        }
    }

    // A helper to get points before returning to the map
    suspend fun getRoutePoints(routeId: Int): List<RoutePoint> {
        return repository.getPointsForRoute(routeId).first()
    }
}
