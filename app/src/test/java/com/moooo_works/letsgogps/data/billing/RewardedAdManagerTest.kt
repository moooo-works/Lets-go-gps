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

        enum class Outcome { LoadFails, LoadOk, InFlight }

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
                Outcome.InFlight -> { /* never resolves — models a real async load in progress */ }
            }
        }
    }

    private class FakeInitializationGate(
        private var result: Boolean? = true
    ) : AdMobInitializationGate {
        var readyChecks = 0
        private val pendingCallbacks = mutableListOf<(Boolean) -> Unit>()

        override fun initialize() = Unit

        override fun whenReady(callback: (Boolean) -> Unit) {
            readyChecks++
            val currentResult = result
            if (currentResult == null) {
                pendingCallbacks += callback
            } else {
                callback(currentResult)
            }
        }

        fun complete(ready: Boolean) {
            result = ready
            pendingCallbacks.toList().also { pendingCallbacks.clear() }
                .forEach { callback -> callback(ready) }
        }
    }

    private fun manager(
        loader: FakeLoader,
        gate: FakeInitializationGate = FakeInitializationGate()
    ) = RewardedAdManager(loader, unitId = "test/123", initializationGate = gate)

    @Test
    fun `preload calls loader once`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val mgr = manager(loader)
        mgr.preload()
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `preload while loading is idempotent`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.InFlight }
        val mgr = manager(loader)
        mgr.preload()
        mgr.preload()
        mgr.preload()
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `preload retries after a previous failure`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadFails }
        val mgr = manager(loader)
        mgr.preload()  // synchronously fails — onFailed resets isLoading
        mgr.preload()  // should retry
        assertEquals(2, loader.loadCalls)
    }

    @Test
    fun `showAd before load triggers onUnavailable and re-preloads`() {
        val loader = FakeLoader()
        val mgr = manager(loader)
        var unavailable = false
        mgr.showAd(activity, onReward = { fail("should not reward") }, onUnavailable = { unavailable = true })
        assertTrue(unavailable)
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `reward callback fires when ad completes`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val mgr = manager(loader)
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
        val mgr = manager(loader)
        mgr.preload()
        var rewarded = false
        mgr.showAd(activity, onReward = { rewarded = true }, onUnavailable = { fail("should be available") })
        // dismiss without invoking reward (user closed mid-ad)
        loader.pendingDismiss?.invoke()
        assertEquals(false, rewarded)
    }

    @Test
    fun `preload waits for initialization and remains idempotent`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val gate = FakeInitializationGate(result = null)
        val mgr = manager(loader, gate)

        mgr.preload()
        mgr.preload()
        mgr.preload()
        assertEquals(1, gate.readyChecks)
        assertEquals(0, loader.loadCalls)

        gate.complete(ready = true)
        assertEquals(1, loader.loadCalls)
    }

    @Test
    fun `initialization failure leaves ad unavailable without loading`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val gate = FakeInitializationGate(result = false)
        val mgr = manager(loader, gate)
        var unavailable = false

        mgr.preload()
        mgr.showAd(
            activity,
            onReward = { fail("should not reward") },
            onUnavailable = { unavailable = true }
        )

        assertTrue(unavailable)
        assertEquals(0, loader.loadCalls)
    }

    @Test
    fun `duplicate reward callbacks only grant once`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val mgr = manager(loader)
        mgr.preload()
        var rewardCount = 0

        mgr.showAd(activity, onReward = { rewardCount++ }, onUnavailable = { fail("should be available") })
        loader.pendingReward?.invoke()
        loader.pendingReward?.invoke()
        loader.pendingDismiss?.invoke()

        assertEquals(1, rewardCount)
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)
}
