package com.example.mockgps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Process
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.example.mockgps.domain.MockPermissionStatus
import java.lang.SecurityException

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidLocationMockEngineTest {
    private val context = mockk<Context>()
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val appOpsManager = mockk<AppOpsManager>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
        every { context.packageName } returns "com.example.mockgps"
        mockkStatic(Process::class)
        every { Process.myUid() } returns 1000
    }

    @Test
    fun `setupMockProvider calls addTestProvider`() {
        val engine = AndroidLocationMockEngine(context)

        engine.setupMockProvider()

        verify {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, 5
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        }
    }

    @Test(expected = SecurityException::class)
    fun `setupMockProvider throws SecurityException if denied`() {
        every {
            locationManager.addTestProvider(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws SecurityException("Denied")

        val engine = AndroidLocationMockEngine(context)
        engine.setupMockProvider()
    }

    @Test
    fun `setLocation emits observable error when LocationManager fails`() = runTest {
        every { locationManager.setTestProviderLocation(any(), any()) } throws IllegalStateException("provider missing")
        val engine = AndroidLocationMockEngine(context)

        val deferredError = async(start = CoroutineStart.UNDISPATCHED) { engine.errors.first() }

        engine.setLocation(25.0, 121.0)

        val error = deferredError.await()
        val cause = (error as MockEngineError.SetLocation).cause
        assertEquals(true, cause.message?.isNotBlank() == true)
    }

    @Test
    fun `teardown emits observable error when remove provider fails`() = runTest {
        every { locationManager.removeTestProvider(any()) } throws IllegalArgumentException("not found")
        val engine = AndroidLocationMockEngine(context)

        val deferredError = async(start = CoroutineStart.UNDISPATCHED) { engine.errors.first() }
        engine.teardownMockProvider()

        val error = deferredError.await()
        val cause = (error as MockEngineError.Teardown).cause
        assertEquals("not found", cause.message)
    }

    @Test
    fun `isMockingAllowed returns true when AppOps allowed`() {
        // Mock API level behavior if strictly needed, or rely on Robolectric environment if configured.
        // Here we mock the AppOpsManager call directly assuming unsafeCheckOpNoThrow or checkOpNoThrow is called.
        // Since we cannot easily control Build.VERSION.SDK_INT in unit tests without Robolectric config or reflection,
        // we mock both paths to be safe or rely on the fact that mockk handles method calls.

        // For simplicity in pure unit test with mockk, we can mock the specific method call.
        // Assuming the test runs on a JVM that defaults to "high enough" SDK or we just mock the calls.

        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.example.mockgps")
        } returns AppOpsManager.MODE_ALLOWED

        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.example.mockgps")
        } returns AppOpsManager.MODE_ALLOWED

        val engine = AndroidLocationMockEngine(context)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.Allowed, status)
    }

    @Test
    fun `isMockingAllowed returns false when AppOps denied`() {
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.example.mockgps")
        } returns AppOpsManager.MODE_ERRORED

        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.example.mockgps")
        } returns AppOpsManager.MODE_ERRORED

        val engine = AndroidLocationMockEngine(context)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.NotAllowed, status)
    }
}
