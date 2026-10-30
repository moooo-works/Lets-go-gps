package com.moooo_works.letsgogps.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import com.moooo_works.letsgogps.MainActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.moooo_works.letsgogps.data.engine.AndroidLocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Opt-in device regression: select the target test app as mock app and grant location first. */
@RunWith(AndroidJUnit4::class)
class LocationRecoveryDeviceTest {
    @Test fun stopFromMainThenRequestFreshRealLocation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = AndroidLocationMockEngine(context)
        assumeTrue(engine.getMockPermissionStatus() == MockPermissionStatus.Allowed)
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val client = LocationServices.getFusedLocationProviderClient(context)
        val terminal = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            engine.setupMockProvider()
            Tasks.await(client.setMockMode(true), 5, TimeUnit.SECONDS)
            engine.setLocation(25.0330, 121.5654, 0f, 0f, 0.0)
            val stopped = CompletableDeferred<Unit>()
            instrumentation.runOnMainSync {
                // Match the real Service's previously failing entry/context combination.
                terminal.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        engine.teardownMockProvider()
                        stopped.complete(Unit)
                    } catch (e: Throwable) {
                        stopped.completeExceptionally(e)
                    }
                }
            }
            withTimeout(10_000) { stopped.await() }
            val location = freshDeviceLocation(client)
            assertNotNull("A fresh real fix must be available without opening Google Maps", location)
            assertFalse("The recovered location must not be mock", LocationCompat.isMock(location!!))
        } finally {
            withContext(NonCancellable) { engine.teardownMockProvider() }
            terminal.cancel()
            activity.close()
        }
    }
}
