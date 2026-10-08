package com.moooo_works.letsgogps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class Android17MockPermissionTest {
    private val context=mockk<Context>(relaxed=true)
    private val ops=mockk<AppOpsManager>(relaxed=true)
    @Before fun setup() {
        mockkStatic(Process::class,Settings.Global::class,LocationServices::class,Log::class)
        every { Process.myUid() } returns 1000
        every { context.packageName } returns "test.app"
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns mockk<LocationManager>(relaxed=true)
        every { context.getSystemService(Context.APP_OPS_SERVICE) } returns ops
        every { LocationServices.getFusedLocationProviderClient(context) } returns mockk(relaxed=true)
        every { Settings.Global.getInt(any(),Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,0) } returns 0
        every { Log.e(any(),any(),any()) } returns 0
        every { ops.unsafeCheckOpNoThrow(any<String>(),any<Int>(),any<String>()) } returns AppOpsManager.MODE_ALLOWED
    }
    @After fun cleanup() { unmockkStatic(Process::class,Settings.Global::class,LocationServices::class,Log::class) }
    @Test fun `hidden developer setting cannot override allowed mock AppOps on Android 17`() {
        assertEquals(MockPermissionStatus.Allowed,AndroidLocationMockEngine(context,37).getMockPermissionStatus())
    }
    @Test fun `Android 17 denied AppOps remains NotAllowed`() {
        every { ops.unsafeCheckOpNoThrow(any<String>(),any<Int>(),any<String>()) } returns AppOpsManager.MODE_ERRORED
        assertEquals(MockPermissionStatus.NotAllowed,AndroidLocationMockEngine(context,37).getMockPermissionStatus())
    }
    @Test fun `Android 17 AppOps failure remains CheckFailed`() {
        every { ops.unsafeCheckOpNoThrow(any<String>(),any<Int>(),any<String>()) } throws SecurityException("unavailable")
        assertTrue(AndroidLocationMockEngine(context,37).getMockPermissionStatus() is MockPermissionStatus.CheckFailed)
    }
}
