package com.example.mockgps.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.data.engine.MockEngineError

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

    fun setMockStatus(status: MockStatus)
    fun setCurrentMockLocation(location: LatLng?)
    fun setMockError(error: MockEngineError?)
    fun clearError()
}
