package com.moooo_works.letsgogps.data.repository

import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.engine.MockEngineError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockStateRepositoryImpl @Inject constructor() : MockStateRepository {
    private val _mockStatus = MutableStateFlow(MockStatus.IDLE)
    override val mockStatus: StateFlow<MockStatus> = _mockStatus.asStateFlow()

    private val _currentMockLocation = MutableStateFlow<LatLng?>(null)
    override val currentMockLocation: StateFlow<LatLng?> = _currentMockLocation.asStateFlow()

    private val _mockError = MutableStateFlow<MockEngineError?>(null)
    override val mockError: StateFlow<MockEngineError?> = _mockError.asStateFlow()

    private val _activeRouteWaypoints = MutableStateFlow<List<LatLng>>(emptyList())
    override val activeRouteWaypoints: StateFlow<List<LatLng>> = _activeRouteWaypoints.asStateFlow()

    override fun setMockStatus(status: MockStatus) {
        _mockStatus.value = status
    }

    override fun setCurrentMockLocation(location: LatLng?) {
        _currentMockLocation.value = location
    }

    override fun setMockError(error: MockEngineError?) {
        _mockError.value = error
    }

    override fun clearError() {
        _mockError.value = null
    }

    override fun setActiveRouteWaypoints(points: List<LatLng>) {
        _activeRouteWaypoints.value = points
    }
}
