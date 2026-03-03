package com.example.mockgps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Process
import android.util.Log
import com.example.mockgps.domain.MockPermissionStatus
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
        mockkStatic(Log::class)
        every { Process.myUid() } returns 1000
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `setupMockProvider calls addTestProvider for gps and network`() {
        val engine = AndroidLocationMockEngine(context)

        engine.setupMockProvider()

        verify {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, 5
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            locationManager.addTestProvider(
                LocationManager.NETWORK_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, 5
            )
            locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
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

        runCatching { engine.setLocation(25.0, 121.0) }

        val error = deferredError.await()
        val cause = (error as MockEngineError.SetLocation).cause
        assertTrue(cause.message?.isNotBlank() == true)
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
        verify { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        verify { locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER) }
    }

    @Test
    fun `isMockingAllowed returns true when AppOps allowed`() {
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
