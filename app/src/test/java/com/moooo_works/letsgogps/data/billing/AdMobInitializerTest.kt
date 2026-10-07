package com.moooo_works.letsgogps.data.billing

import android.app.Application
import android.content.Context
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.OnAdapterInitializationCompleteListener
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AdMobInitializerTest {
    private val consentState = MutableStateFlow(AdConsentState())
    private val consent = mockk<AdConsentManager>()
    private val listener = slot<OnAdapterInitializationCompleteListener>()
    private val initialized = CountDownLatch(1)
    private lateinit var initializer: AdMobInitializer

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { consent.state } returns consentState
        mockkObject(MobileAds.Companion)
        every { MobileAds.initialize(any(), any(), capture(listener)) } answers { initialized.countDown() }
        initializer = AdMobInitializer(mockk<Context>(relaxed = true), consent)
    }

    @After fun teardown() {
        unmockkObject(MobileAds.Companion)
        Dispatchers.resetMain()
    }

    @Test fun `unknown consent cannot initialize through any entry point`() {
        initializer.initialize()
        var ready: Boolean? = null
        initializer.whenReady { ready = it }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, ready)
        verify(exactly = 0) { MobileAds.initialize(any(), any(), any()) }
    }

    @Test fun `repeated allows initialize only once`() {
        consentState.value = AdConsentState(canRequestAds = true, revision = 1)
        assertTrue(initialized.await(3, TimeUnit.SECONDS))
        initializer.initialize()
        consentState.value = AdConsentState(canRequestAds = true, revision = 2)
        listener.captured.onAdapterInitializationComplete(mockk(relaxed = true))
        verify(exactly = 1) { MobileAds.initialize(any(), any(), any()) }
        assertEquals(AdMobInitializationState.Ready, initializer.state.value)
    }

    @Test fun `initialization completion after revocation cannot permit waiting requests`() {
        consentState.value = AdConsentState(canRequestAds = true, revision = 1)
        assertTrue(initialized.await(3, TimeUnit.SECONDS))
        var ready: Boolean? = null
        initializer.whenReady { ready = it }
        consentState.value = AdConsentState(canRequestAds = false, revision = 2)
        listener.captured.onAdapterInitializationComplete(mockk(relaxed = true))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, ready)
        initializer.whenReady { ready = it }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, ready)
    }
}
