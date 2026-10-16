package com.moooo_works.letsgogps.service

import android.app.Application
import android.content.Intent
import android.os.Looper
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MockLocationForegroundStartupTest {
    private fun withService(check: (MockLocationService) -> Unit) {
        val controller = Robolectric.buildService(MockLocationService::class.java)
        val service = controller.get()
        // Exercise the real lifecycle with explicit dependencies, without a Hilt application.
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
        service.mockEngine = mockk(relaxed = true)
        service.mockSessionRepository = mockk(relaxed = true)
        controller.create()
        try {
            check(service)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `stop targets only its delivered command and preserves a queued newer start`() = withService { service ->
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_STOP), 0, 2)
        assertEquals("A newer start may already be queued in Android", 2, shadowOf(service).stopSelfId)
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    private fun backgroundStop(service: MockLocationService, startId: Int) {
        val stop = MockLocationService::class.java.getDeclaredMethod("handleStop", Int::class.javaPrimitiveType)
        stop.isAccessible = true
        Thread { stop.invoke(service, startId) }.apply { start(); join() }
    }

    @Test
    fun `background stop waits for main thread and uses its original command id`() = withService { service ->
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 7)
        backgroundStop(service, 7)
        assertFalse(shadowOf(service).isStoppedBySelf)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(7, shadowOf(service).stopSelfId)
    }

    @Test
    fun `queued old background stop cannot tear down a newer command`() = withService { service ->
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 7)
        backgroundStop(service, 7)
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 8)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(shadowOf(service).isStoppedBySelf)
        verify(exactly = 0) { service.mockStateRepository.setCurrentMockLocation(null) }
    }

    @Test
    fun `stop publishes idle only after the system stop request`() = withService { service ->
        every { service.mockStateRepository.setMockStatus(MockStatus.IDLE) } answers {
            assertTrue(shadowOf(service).isStoppedBySelf)
        }
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_STOP), 0, 3)
        every { service.mockStateRepository.setMockStatus(MockStatus.IDLE) } returns Unit
    }

    @Test
    fun `queued route completion cannot clear a newer session`() = withService { service ->
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 7)
        val complete = MockLocationService::class.java.getDeclaredMethod("handleRouteCompleted", Int::class.javaPrimitiveType)
        complete.isAccessible = true
        Thread { complete.invoke(service, 7) }.apply { start(); join() }
        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 8)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(shadowOf(service).isStoppedBySelf)
        io.mockk.coVerify(exactly = 0) { service.mockSessionRepository.clear() }
    }

    @Test
    fun `foreground notification is posted before location provider setup`() = withService { service ->
        // Legacy resources cannot resolve app strings; keep the actual notification/FGS APIs.
        val startedService = spyk(service)
        every { startedService.getString(any()) } returns "Mock location"
        every { startedService.mockEngine.setupMockProvider() } answers {
            assertNotNull(shadowOf(startedService).lastForegroundNotification)
        }
        startedService.onStartCommand(Intent().setAction(MockLocationService.ACTION_START_SINGLE), 0, 1)
        verify(exactly = 1) { startedService.mockEngine.setupMockProvider() }
    }
}
