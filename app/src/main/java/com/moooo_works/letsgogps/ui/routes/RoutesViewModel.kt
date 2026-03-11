package com.moooo_works.letsgogps.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutesUiState(
    val routes: List<RouteSummary> = emptyList()
)

@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val repository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutesUiState())
    val uiState: StateFlow<RoutesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRoutes().collect { routes ->
                _uiState.update { it.copy(routes = routes) }
            }
        }
    }

    fun renameRoute(routeId: Int, name: String) {
        viewModelScope.launch {
            repository.updateRouteName(routeId, name)
        }
    }

    fun deleteRoute(routeId: Int) {
        viewModelScope.launch {
            repository.deleteRoute(routeId)
        }
    }
}
