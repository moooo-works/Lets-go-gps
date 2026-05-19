package com.moooo_works.letsgogps.data.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdUnlockStoreTest {

    private lateinit var context: Context
    private lateinit var store: AdUnlockStore
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        store = AdUnlockStore(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Clean up DataStore file between tests so each test starts fresh.
        File(context.filesDir, "datastore/ad_unlock.preferences_pb").delete()
    }

    @Test
    fun `default expiry is zero`() = runTest(testDispatcher) {
        assertEquals(0L, store.expiryFlow.first())
    }

    @Test
    fun `setExpiry persists value`() = runTest(testDispatcher) {
        store.setExpiry(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, store.expiryFlow.first())
    }

    @Test
    fun `setExpiry overwrites previous value`() = runTest(testDispatcher) {
        store.setExpiry(1_000L)
        store.setExpiry(2_000L)
        assertEquals(2_000L, store.expiryFlow.first())
    }
}
