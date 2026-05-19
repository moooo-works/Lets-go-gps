package com.moooo_works.letsgogps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.provider.Settings
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
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
import androidx.test.core.app.ApplicationProvider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidLocationMockEngineTest {
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val appOpsManager = mockk<AppOpsManager>(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setup() {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        context = spyk(realContext)
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
        // Default: developer mode enabled — individual tests can override.
        Settings.Global.putInt(
            realContext.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            1
        )
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
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, any<Int>(), any())
        } returns AppOpsManager.MODE_ALLOWED

        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, any<Int>(), any())
        } returns AppOpsManager.MODE_ALLOWED

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.Allowed, status)
    }

    @Test
    fun `isMockingAllowed returns false when AppOps denied`() {
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, any<Int>(), any())
        } returns AppOpsManager.MODE_ERRORED

        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, any<Int>(), any())
        } returns AppOpsManager.MODE_ERRORED

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.NotAllowed, status)
    }

    @Test
    fun `getMockPermissionStatus returns DeveloperModeDisabled when developer settings off`() {
        Settings.Global.putInt(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        )

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertEquals(MockPermissionStatus.DeveloperModeDisabled, status)
        // AppOps must NOT be consulted when developer mode is off.
        verify(exactly = 0) {
            appOpsManager.unsafeCheckOpNoThrow(any(), any(), any<String>())
        }
    }

    @Test
    fun `getMockPermissionStatus returns CheckFailed when AppOps check throws`() {
        // Tests the outer try/catch in getMockPermissionStatus(): if AppOps itself
        // throws, the engine returns CheckFailed rather than propagating the crash.
        every {
            appOpsManager.unsafeCheckOpNoThrow(any(), any<Int>(), any())
        } throws RuntimeException("appops failure")

        val engine = AndroidLocationMockEngine(context, sdkInt = 33)
        val status = engine.getMockPermissionStatus()

        assertTrue(status is MockPermissionStatus.CheckFailed)
    }

    private companion object {
        private const val LEGACY_ACCURACY_FINE = 1
    }
}
