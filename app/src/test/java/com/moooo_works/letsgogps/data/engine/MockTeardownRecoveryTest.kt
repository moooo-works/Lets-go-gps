package com.moooo_works.letsgogps.data.engine

import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import io.mockk.*
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.google.android.gms.tasks.TaskCompletionSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class MockTeardownRecoveryTest {
    private fun engine(manager: LocationManager, fused: FusedLocationProviderClient): AndroidLocationMockEngine {
        val context = spyk(ApplicationProvider.getApplicationContext<Context>())
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns manager
        val engine = AndroidLocationMockEngine(context, 28)
        engine.javaClass.getDeclaredField("fusedClient").apply { isAccessible = true }.set(engine, fused)
        return engine
    }
    @Test fun `failed provider removal must retain provider for retry`() = runBlocking {
        val manager = mockk<LocationManager>(relaxed = true)
        every { manager.removeTestProvider("gps") } throws IllegalStateException("injected removal failure")
        val fused = mockk<FusedLocationProviderClient>()
        every { fused.setMockMode(false) } returns Tasks.forResult(null)
        val engine = engine(manager, fused)
        val field = engine.javaClass.getDeclaredField("enabledProviders").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val providers = field.get(engine) as MutableSet<String>
        providers.add("gps")
        runCatching { engine.teardownMockProvider() }
        assertTrue("Failed provider must remain tracked for retry", providers.contains("gps"))
        engine.setLocation(25.0, 121.0)
        verify(exactly = 0) { manager.setTestProviderLocation(any(), any()) }
        every { manager.removeTestProvider("gps") } just Runs
        engine.teardownMockProvider()
        assertFalse(providers.contains("gps"))
        verify(exactly = 2) { manager.removeTestProvider("gps") }
    }
    @Test fun `FLP task failure must be observable to caller`() = runBlocking {
        val manager = mockk<LocationManager>(relaxed = true)
        val fused = mockk<FusedLocationProviderClient>()
        every { fused.setMockMode(false) } returns Tasks.forException<Void>(IllegalStateException("injected asynchronous exit failure"))
        val engine = engine(manager, fused)
        val error = runCatching { engine.teardownMockProvider() }.exceptionOrNull()
        assertNotNull("A completed failed FLP exit task must not be reported as successful teardown", error)
    }
    @Test fun `teardown does not finish before FLP confirms exit`() = runBlocking {
        val manager = mockk<LocationManager>(relaxed = true)
        val fused = mockk<FusedLocationProviderClient>()
        val completion = TaskCompletionSource<Void>()
        every { fused.setMockMode(false) } returns completion.task
        val engine = engine(manager, fused)
        val cleanup = async { engine.teardownMockProvider() }
        delay(50)
        assertFalse(cleanup.isCompleted)
        assertNotNull(runCatching { engine.setupMockProvider() }.exceptionOrNull())
        completion.setResult(null)
        cleanup.await()
    }
    @Test fun `unresponsive FLP times out instead of reporting success`() = runBlocking {
        val manager = mockk<LocationManager>(relaxed = true)
        val fused = mockk<FusedLocationProviderClient>()
        every { fused.setMockMode(false) } returns TaskCompletionSource<Void>().task
        val error = runCatching { engine(manager, fused).teardownMockProvider() }.exceptionOrNull()
        assertNotNull(error)
        assertTrue("Unexpected failure: $error caused by ${error?.cause}", generateSequence(error) { it.cause }.any { it is kotlinx.coroutines.TimeoutCancellationException })
    }

    @Test fun `unavailable FLP that never enabled mock does not trap provider-only devices`() = runBlocking {
        val manager = mockk<LocationManager>(relaxed = true)
        val fused = mockk<FusedLocationProviderClient>()
        every { fused.setMockMode(true) } returns Tasks.forException<Void>(IllegalStateException("API unavailable"))
        every { fused.setMockMode(false) } returns Tasks.forException<Void>(IllegalStateException("API unavailable"))
        val engine = engine(manager, fused)
        engine.setupMockProvider()
        engine.teardownMockProvider()
        verify { manager.removeTestProvider("gps") }
    }

}
