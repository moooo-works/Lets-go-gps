package com.example.mockgps.domain

interface LocationMockEngine {
    fun setupMockProvider()
    fun teardownMockProvider()
    fun setLocation(latitude: Double, longitude: Double)
    fun isMockingAllowed(): Boolean
}
