package com.moooo_works.letsgogps.data.engine

import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class MockTeardownProbeTest {
    private fun engine(manager: LocationManager, fused: FusedLocationProviderClient): AndroidLocationMockEngine {
        val context = spyk(ApplicationProvider.getApplicationContext<Context>())
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns manager
        val engine = AndroidLocationMockEngine(context, 28)
        engine.javaClass.getDeclaredField("fusedClient").apply { isAccessible = true }.set(engine, fused)
        return engine
    }
    @Test fun `failed provider removal must retain provider for retry`() {
        val manager = mockk<LocationManager>(relaxed = true)
        every { manager.removeTestProvider("gps") } throws IllegalStateException("injected removal failure")
        val fused = mockk<FusedLocationProviderClient>()
        every { fused.setMockMode(false) } returns Tasks.forResult(null)
        val engine = engine(manager, fused)
        val field = engine.javaClass.getDeclaredField("enabledProviders").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val providers = field.get(engine) as MutableSet<String>
        providers.add("gps")
        engine.teardownMockProvider()
        assertTrue("Failed provider must remain tracked for retry", providers.contains("gps"))
    }
    @Test fun `FLP task failure must be observable to caller`() {
        val manager = mockk<LocationManager>(relaxed = true)
        val fused = mockk<FusedLocationProviderClient>()
        every { fused.setMockMode(false) } returns Tasks.forException<Void>(IllegalStateException("injected asynchronous exit failure"))
        val engine = engine(manager, fused)
        val error = runCatching { engine.teardownMockProvider() }.exceptionOrNull()
        assertNotNull("A completed failed FLP exit task must not be reported as successful teardown", error)
    }
}
