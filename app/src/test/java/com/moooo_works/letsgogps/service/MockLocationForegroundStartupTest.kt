package com.moooo_works.letsgogps.service

import android.app.Application
import android.content.Intent
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MockLocationForegroundStartupTest {
    @Test
    fun `stop targets only its delivered command and preserves a queued newer start`() {
        val controller = Robolectric.buildService(MockLocationService::class.java)
        val service = controller.get()
        // Test the real lifecycle with explicit dependencies rather than a Hilt application.
        ReflectionHelpers.setField(service, "injected", true)
        val state = mockk<MockStateRepository>(relaxed = true)
        every { state.mockStatus } returns MutableStateFlow(MockStatus.IDLE)
        every { state.currentMockLocation } returns MutableStateFlow(null)
        val simulator = mockk<RouteSimulator>(relaxed = true)
        every { simulator.simulationState } returns MutableStateFlow(SimulationState.IDLE)
        every { simulator.routeProgress } returns MutableStateFlow(null)
        service.mockStateRepository = state
        service.routeSimulator = simulator
        service.settingsRepository = mockk<SettingsRepository>(relaxed = true)
        controller.create()
        try {
            service.onStartCommand(Intent().setAction(MockLocationService.ACTION_STOP), 0, 2)
            assertEquals("A newer start may already be queued in Android", 2, shadowOf(service).stopSelfId)
        } finally {
            controller.destroy()
        }
    }
}
