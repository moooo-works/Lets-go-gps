package com.example.mockgps.domain

sealed class MockPermissionStatus {
    object Allowed : MockPermissionStatus()
    object NotAllowed : MockPermissionStatus()
    data class CheckFailed(val cause: Throwable) : MockPermissionStatus()
}

interface LocationMockEngine {
    fun setupMockProvider()
    fun teardownMockProvider()
    fun setLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float = 0f,
        speed: Float = 0f,
        altitude: Double = 0.0
    )
    fun getMockPermissionStatus(): MockPermissionStatus
}
