package com.moooo_works.letsgogps.data.healthcheck

import android.app.AppOpsManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckItem
import com.moooo_works.letsgogps.domain.healthcheck.ItemStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemHealthCheckImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val appOpsManager = mockk<AppOpsManager>(relaxed = true)
    private val powerManager = mockk<PowerManager>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns appOpsManager
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { context.contentResolver } returns contentResolver
        every { context.packageName } returns "com.moooo_works.letsgogps"

        mockkStatic(Process::class)
        mockkStatic(Settings.Global::class)
        every { Process.myUid() } returns 1000
        every { Process.myPid() } returns 1

        // Defaults: every check passes. Individual tests override the one they care about.
        every {
            Settings.Global.getInt(any<ContentResolver>(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 1
        every { context.checkPermission(any<String>(), any<Int>(), any<Int>()) } returns PackageManager.PERMISSION_GRANTED
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ALLOWED
        every {
            appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ALLOWED
        every { powerManager.isIgnoringBatteryOptimizations("com.moooo_works.letsgogps") } returns true
    }

    @Test
    fun `all green on android 13`() {
        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.TIRAMISU).refresh()

        HealthCheckItem.values().forEach { item ->
            assertEquals("Item $item should pass", ItemStatus.Passed, state.statusOf(item))
        }
        assertTrue(state.allPassed)
        assertFalse(state.hasBlockingFailure)
    }

    @Test
    fun `notification check is NotApplicable on android 12`() {
        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.S).refresh()

        assertEquals(ItemStatus.NotApplicable, state.statusOf(HealthCheckItem.NotificationPermission))
        assertTrue("All-passed must treat NotApplicable as ok", state.allPassed)
    }

    @Test
    fun `developer mode disabled is a blocking failure`() {
        every {
            Settings.Global.getInt(any<ContentResolver>(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 0

        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.TIRAMISU).refresh()

        assertEquals(ItemStatus.Failed, state.statusOf(HealthCheckItem.DeveloperMode))
        assertTrue("Critical failure must block start", state.hasBlockingFailure)
        assertFalse(state.allPassed)
    }

    @Test
    fun `gps disabled is a blocking failure`() {
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns false

        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.TIRAMISU).refresh()

        assertEquals(ItemStatus.Failed, state.statusOf(HealthCheckItem.GpsEnabled))
        assertTrue(state.hasBlockingFailure)
    }

    @Test
    fun `mock app not selected is a blocking failure`() {
        every {
            appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, 1000, "com.moooo_works.letsgogps")
        } returns AppOpsManager.MODE_ERRORED

        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.TIRAMISU).refresh()

        assertEquals(ItemStatus.Failed, state.statusOf(HealthCheckItem.MockAppSelected))
        assertTrue(state.hasBlockingFailure)
    }

    @Test
    fun `battery exemption failure is soft — does NOT block start`() {
        every { powerManager.isIgnoringBatteryOptimizations("com.moooo_works.letsgogps") } returns false

        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.TIRAMISU).refresh()

        assertEquals(ItemStatus.Failed, state.statusOf(HealthCheckItem.BatteryOptimizationExempt))
        assertFalse("Battery exemption is non-critical", state.hasBlockingFailure)
        assertFalse(state.allPassed)
    }

    @Test
    fun `developer mode lookup exception fails open`() {
        every {
            Settings.Global.getInt(any<ContentResolver>(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } throws RuntimeException("setting not found on this ROM")

        val state = SystemHealthCheckImpl(context, sdkInt = Build.VERSION_CODES.TIRAMISU).refresh()

        // Fail-open: don't block users on weird ROMs; AppOps still gates real misuse.
        assertEquals(ItemStatus.Passed, state.statusOf(HealthCheckItem.DeveloperMode))
    }
}
