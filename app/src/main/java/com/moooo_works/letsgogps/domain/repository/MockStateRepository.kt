package com.moooo_works.letsgogps.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.data.engine.MockEngineError

enum class MockStatus {
    IDLE,
    MOCKING,
    ROUTE_PLAYING,
    ROUTE_PAUSED
}

interface MockStateRepository {
    val mockStatus: StateFlow<MockStatus>
    val currentMockLocation: StateFlow<LatLng?>
    val mockError: StateFlow<MockEngineError?>
    val activeRouteWaypoints: StateFlow<List<LatLng>>

    fun setMockStatus(status: MockStatus)
    fun setCurrentMockLocation(location: LatLng?)
    fun setMockError(error: MockEngineError?)
    fun clearError()
    fun setActiveRouteWaypoints(points: List<LatLng>)
}
