package com.example.mockgps.data.engine

import android.content.Context
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.SecurityException

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidLocationMockEngineTest {
    private val context = mockk<Context>()
    private val locationManager = mockk<LocationManager>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
    }

    @Test
    fun `setupMockProvider calls addTestProvider`() {
        val engine = AndroidLocationMockEngine(context)

        engine.setupMockProvider()

        verify {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, 0, 5
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
    fun `isMockingAllowed returns false for SecurityException and emits error`() = runTest {
        every {
            locationManager.addTestProvider(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws SecurityException("denied")
        val engine = AndroidLocationMockEngine(context)

        val deferredError = async(start = CoroutineStart.UNDISPATCHED) { engine.errors.first() }
        val allowed = engine.isMockingAllowed()

        val error = deferredError.await()
        assertEquals(false, allowed)
        val cause = (error as MockEngineError.PermissionCheck).cause
        assertEquals("denied", cause.message)
    }
}
