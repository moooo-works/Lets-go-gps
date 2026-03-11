package com.moooo_works.letsgogps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Process
import android.util.Log
import com.moooo_works.letsgogps.domain.MockPermissionStatus
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidLocationMockEngineTest {
    private val context = mockk<Context>()
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val appOpsManager = mockk<AppOpsManager>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
        every { context.packageName } returns "com.moooo_works.letsgogps"
        mockkStatic(Process::class)
        mockkStatic(Log::class)
        every { Process.myUid() } returns 1000
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `setupMockProvider uses ProviderProperties API on android 12 plus`() {
        val engine = AndroidLocationMockEngine(context, sdkInt = 33)

        engine.setupMockProvider()

        verify { locationManager.addTestProvider(LocationManager.GPS_PROVIDER, any<ProviderProperties>()) }
        verify { locationManager.addTestProvider(LocationManager.NETWORK_PROVIDER, any<ProviderProperties>()) }
        verify(exactly = 0) {
            locationManager.addTestProvider(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `setupMockProvider uses legacy API below android 12`() {
        val engine = AndroidLocationMockEngine(context, sdkInt = 30)

        engine.setupMockProvider()

        verify {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, 5
            )
            locationManager.addTestProvider(
                LocationManager.NETWORK_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, 5
            )
        }
    }

    @Test
    fun `setup with one provider failure still leaves enabled providers`() {
        every { locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true) } throws IllegalArgumentException("gps bad")
        val engine = AndroidLocationMockEngine(context, sdkInt = 33)

        engine.setupMockProvider()
        engine.setLocation(25.0, 121.0)

        verify(exactly = 0) { locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, any()) }
        verify(atLeast = 1) { locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, any()) }
    }

    @Test
    fun `setup all failure reports no test providers and setLocation skips push`() = runTest {
        every { locationManager.setTestProviderEnabled(any(), true) } throws IllegalArgumentException("enable failed")
        val engine = AndroidLocationMockEngine(context, sdkInt = 33)

        runCatching { engine.setupMockProvider() }

        val deferredError = async(start = CoroutineStart.UNDISPATCHED) { engine.errors.first() }
        runCatching { engine.setLocation(25.0, 121.0) }
        val error = deferredError.await()

        assertTrue(error is MockEngineError.Setup)
        verify(exactly = 0) { locationManager.setTestProviderLocation(any(), any()) }
    }

    @Test
    fun `isMockingAllowed returns true when AppOps allowed`() {
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ALLOWED

        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ALLOWED

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.Allowed, status)
    }

    @Test
    fun `isMockingAllowed returns false when AppOps denied`() {
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ERRORED

        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ERRORED

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.NotAllowed, status)
    }
}
