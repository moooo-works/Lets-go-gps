package com.moooo_works.letsgogps.ui.map

import android.location.Location
import android.os.Looper
import androidx.core.location.LocationCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class FreshDeviceLocationTest {
    @Test fun `requests fresh fix and accepts real location`() = runTest {
        val client = mockk<FusedLocationProviderClient>()
        val request = slot<CurrentLocationRequest>()
        val real = Location("gps")
        every { client.getCurrentLocation(capture(request), any<CancellationToken>()) } returns Tasks.forResult(real)
        val result = async(start = CoroutineStart.UNDISPATCHED) { freshDeviceLocation(client) }
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(real, result.await())
        assertEquals(0L, request.captured.maxUpdateAgeMillis)
        assertEquals(15_000L, request.captured.durationMillis)
    }
    @Test fun `mock or missing fix never becomes current real position`() = runTest {
        val client = mockk<FusedLocationProviderClient>()
        val mock = Location("fused").apply { LocationCompat.setMock(this, true) }
        for (location in listOf(mock, null)) {
            every { client.getCurrentLocation(any<CurrentLocationRequest>(), any<CancellationToken>()) } returns Tasks.forResult(location)
            val result = async(start = CoroutineStart.UNDISPATCHED) { freshDeviceLocation(client) }
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(result.await())
        }
    }
    @Test fun `timeout cancels request without returning old fix`() = runTest {
        val client = mockk<FusedLocationProviderClient>()
        val token = slot<CancellationToken>()
        every { client.getCurrentLocation(any<CurrentLocationRequest>(), capture(token)) } returns TaskCompletionSource<Location>().task
        val result = async(start = CoroutineStart.UNDISPATCHED) { freshDeviceLocation(client) }
        advanceTimeBy(16_001)
        assertNull(result.await())
        assertTrue(token.captured.isCancellationRequested)
    }
    @Test fun `permission failure remains an error instead of cached fallback`() = runTest {
        val client = mockk<FusedLocationProviderClient>()
        every { client.getCurrentLocation(any<CurrentLocationRequest>(), any<CancellationToken>()) } throws SecurityException("denied")
        assertTrue(runCatching { freshDeviceLocation(client) }.exceptionOrNull() is SecurityException)
    }
    @Test fun `cancelled provider task returns no fix so UI can explain failure`() = runTest {
        val client = mockk<FusedLocationProviderClient>()
        every { client.getCurrentLocation(any<CurrentLocationRequest>(), any<CancellationToken>()) } returns Tasks.forCanceled()
        val result = async(start = CoroutineStart.UNDISPATCHED) { freshDeviceLocation(client) }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(result.await())
    }

}
