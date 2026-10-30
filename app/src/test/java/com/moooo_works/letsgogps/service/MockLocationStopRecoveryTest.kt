package com.moooo_works.letsgogps.service

import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class MockLocationStopRecoveryTest {
    private fun field(service: MockLocationService, name: String) =
        MockLocationService::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun service(engine: LocationMockEngine, state: MockStateRepository): MockLocationService =
        Robolectric.buildService(MockLocationService::class.java).get().apply {
            mockEngine = engine
            every { engine.getMockPermissionStatus() } returns com.moooo_works.letsgogps.domain.MockPermissionStatus.Allowed
            mockStateRepository = state
            routeSimulator = mockk<RouteSimulator>(relaxed = true)
            field(this, "isProviderSetup").set(this, true)
        }
    private fun stop(service: MockLocationService) {
        MockLocationService::class.java.getDeclaredMethod("handleStop").apply { isAccessible = true }.invoke(service)
    }
    private fun settled(service: MockLocationService) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (field(service, "cleanupInProgress").getBoolean(service) && System.nanoTime() < until) Thread.sleep(10)
        assertFalse(field(service, "cleanupInProgress").getBoolean(service))
    }
    @Test fun `failed stop retains setup and next stop retries cleanup`() {
        val engine = mockk<LocationMockEngine>()
        val state = mockk<MockStateRepository>(relaxed = true)
        val failed = CountDownLatch(1)
        every { state.setMockError(any()) } answers { failed.countDown() }
        coEvery { engine.teardownMockProvider() } throws IllegalStateException("cleanup failed")
        val service = service(engine, state)
        stop(service)
        assertTrue(failed.await(3, TimeUnit.SECONDS))
        settled(service)
        assertTrue(field(service, "isProviderSetup").getBoolean(service))
        verify(exactly = 0) { state.setMockStatus(MockStatus.IDLE) }
        coEvery { engine.teardownMockProvider() } just Runs
        stop(service)
        settled(service)
        assertFalse(field(service, "isProviderSetup").getBoolean(service))
        coVerify(exactly = 2) { engine.teardownMockProvider() }
        verify(exactly = 1) { state.setMockStatus(MockStatus.IDLE) }
    }
    @Test fun `duplicate stop and destroy do not cancel or duplicate in-flight cleanup`() {
        val engine = mockk<LocationMockEngine>()
        val state = mockk<MockStateRepository>(relaxed = true)
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        coEvery { engine.teardownMockProvider() } coAnswers { entered.countDown(); gate.await() }
        val service = service(engine, state)
        stop(service)
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        stop(service)
        service.onDestroy()
        assertTrue(field(service, "isProviderSetup").getBoolean(service))
        verify(exactly = 0) { state.setMockStatus(MockStatus.IDLE) }
        gate.complete(Unit)
        settled(service)
        coVerify(exactly = 1) { engine.teardownMockProvider() }
        verify(exactly = 1) { state.setMockStatus(MockStatus.IDLE) }
    }
    @Test fun `revoked permission exits locally with failure retained rather than trapping service`() {
        val engine = mockk<LocationMockEngine>()
        val state = mockk<MockStateRepository>(relaxed = true)
        coEvery { engine.teardownMockProvider() } throws SecurityException("revoked")
        val service = service(engine, state)
        every { engine.getMockPermissionStatus() } returns com.moooo_works.letsgogps.domain.MockPermissionStatus.NotAllowed
        stop(service)
        settled(service)
        verify(exactly = 1) { state.setMockError(any()) }
        verify(exactly = 1) { state.setMockStatus(MockStatus.IDLE) }
        assertTrue(field(service, "cleanupCompleted").getBoolean(service))
    }
    @Test fun `destroy after completed cleanup cannot clear a later mock session`() {
        val engine = mockk<LocationMockEngine>()
        val state = mockk<MockStateRepository>(relaxed = true)
        coEvery { engine.teardownMockProvider() } just Runs
        val service = service(engine, state)
        stop(service)
        settled(service)
        service.onDestroy()
        coVerify(exactly = 1) { engine.teardownMockProvider() }
        verify(exactly = 1) { state.setMockStatus(MockStatus.IDLE) }
    }

}
