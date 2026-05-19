package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardedAdManagerTest {

    private val activity = mockk<Activity>(relaxed = true)

    private class FakeLoader : RewardedAdManager.RewardedAdLoader {
        var loadCalls = 0
        var nextOutcome: Outcome = Outcome.LoadFails
        var pendingReward: (() -> Unit)? = null
        var pendingDismiss: (() -> Unit)? = null

        enum class Outcome { LoadFails, LoadOk }

        override fun load(unitId: String, onLoaded: (RewardedAdManager.LoadedAd) -> Unit, onFailed: () -> Unit) {
            loadCalls++
            when (nextOutcome) {
                Outcome.LoadFails -> onFailed()
                Outcome.LoadOk -> onLoaded(object : RewardedAdManager.LoadedAd {
                    override fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit) {
                        pendingReward = onReward
                        pendingDismiss = onDismiss
                    }
                })
            }
        }
    }

    @Test
    fun `preload calls loader once`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val mgr = RewardedAdManager(loader, unitId = "test/123")
        mgr.preload()
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `preload while loading is idempotent`() {
        val loader = FakeLoader()  // load never completes
        val mgr = RewardedAdManager(loader, unitId = "test/123")
        mgr.preload()
        mgr.preload()
        mgr.preload()
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `showAd before load triggers onUnavailable and re-preloads`() {
        val loader = FakeLoader()
        val mgr = RewardedAdManager(loader, unitId = "test/123")
        var unavailable = false
        mgr.showAd(activity, onReward = { fail("should not reward") }, onUnavailable = { unavailable = true })
        assertTrue(unavailable)
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `reward callback fires when ad completes`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val mgr = RewardedAdManager(loader, unitId = "test/123")
        mgr.preload()
        var rewarded = false
        mgr.showAd(activity, onReward = { rewarded = true }, onUnavailable = { fail("should be available") })
        loader.pendingReward?.invoke()
        loader.pendingDismiss?.invoke()
        assertTrue(rewarded)
    }

    @Test
    fun `dismiss without reward does not invoke onReward`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val mgr = RewardedAdManager(loader, unitId = "test/123")
        mgr.preload()
        var rewarded = false
        mgr.showAd(activity, onReward = { rewarded = true }, onUnavailable = { fail("should be available") })
        // dismiss without invoking reward (user closed mid-ad)
        loader.pendingDismiss?.invoke()
        assertEquals(false, rewarded)
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)
}
