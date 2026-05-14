package com.moooo_works.letsgogps.data.engine

import android.app.AppOpsManager
import android.content.ContentResolver
import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Process
import android.provider.Settings
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
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
        every { context.packageName } returns "com.moooo_works.letsgogps"
        every { context.contentResolver } returns contentResolver
        mockkStatic(Process::class)
        mockkStatic(Log::class)
        mockkStatic(Settings.Global::class)
        every { Process.myUid() } returns 1000
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        // Default: developer mode enabled — individual tests can override.
        every {
            Settings.Global.getInt(any<ContentResolver>(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 1
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
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, LEGACY_ACCURACY_FINE
            )
            locationManager.addTestProvider(
                LocationManager.NETWORK_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, LEGACY_ACCURACY_FINE
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

    @Test
    fun `getMockPermissionStatus returns DeveloperModeDisabled when developer settings off`() {
        every {
            Settings.Global.getInt(any<ContentResolver>(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 0

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.DeveloperModeDisabled, status)
        // AppOps must NOT be consulted when developer mode is off.
        verify(exactly = 0) {
            appOpsManager.unsafeCheckOpNoThrow(any(), any(), any<String>())
        }
    }

    @Test
    fun `getMockPermissionStatus falls through to AppOps when settings lookup throws`() {
        every {
            Settings.Global.getInt(any<ContentResolver>(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } throws RuntimeException("setting not found")
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ALLOWED

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        // Fail-open: rare ROMs that throw on the lookup should not block legit users.
        assertEquals(MockPermissionStatus.Allowed, status)
    }

    private companion object {
        private const val LEGACY_ACCURACY_FINE = 1
    }
}
