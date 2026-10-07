package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
        var destroyCalls = 0
        val loadCallbacks = mutableListOf<(RewardedAdManager.LoadedAd) -> Unit>()
        val failureCallbacks = mutableListOf<() -> Unit>()

        fun ad() = object : RewardedAdManager.LoadedAd {
            override fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit) {
                pendingReward = onReward
                pendingDismiss = onDismiss
            }
            override fun destroy() { destroyCalls++ }
        }

        enum class Outcome { LoadFails, LoadOk, InFlight }

        override fun load(unitId: String, onLoaded: (RewardedAdManager.LoadedAd) -> Unit, onFailed: () -> Unit) {
            loadCalls++
            loadCallbacks += onLoaded
            failureCallbacks += onFailed
            when (nextOutcome) {
                Outcome.LoadFails -> onFailed()
                Outcome.LoadOk -> onLoaded(ad())
                Outcome.InFlight -> { /* never resolves — models a real async load in progress */ }
            }
        }
    }

    private class FakeConsent : AdConsentGate {
        override val state = MutableStateFlow(AdConsentState(canRequestAds = true))
        var privacyReady = true
        var preparations = 0
        override fun prepareAdRequest(): Boolean {
            preparations++
            return state.value.canRequestAds && privacyReady
        }
        fun update(allowed: Boolean) {
            state.value = state.value.copy(canRequestAds = allowed, revision = state.value.revision + 1)
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
        gate: FakeInitializationGate = FakeInitializationGate(),
        consent: FakeConsent = FakeConsent()
    ) = RewardedAdManager(loader, unitId = "test/123", initializationGate = gate,
        consent = consent, scope = CoroutineScope(Dispatchers.Unconfined))

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

    @Test fun `denied consent never loads or shows`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val consent = FakeConsent().apply { update(false) }
        val mgr = manager(loader, consent = consent)
        mgr.preload()
        var unavailable = false
        mgr.showAd(activity, { fail("must not reward") }, { unavailable = true })
        assertEquals(0, loader.loadCalls)
        assertTrue(unavailable)
    }

    @Test fun `privacy change destroys loaded ad and requires fresh load even if allowed`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val consent = FakeConsent()
        val mgr = manager(loader, consent = consent)
        mgr.preload()
        consent.update(true)
        assertEquals(1, loader.destroyCalls)
        var unavailable = false
        mgr.showAd(activity, { fail("old ad must not show") }, { unavailable = true })
        assertTrue(unavailable)
        assertEquals(2, loader.loadCalls)
    }

    @Test fun `late load across consent revision is destroyed without replacing new ad`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.InFlight }
        val consent = FakeConsent()
        val mgr = manager(loader, consent = consent)
        mgr.preload()
        consent.update(false)
        consent.update(true)
        mgr.preload()
        loader.loadCallbacks[0](loader.ad())
        loader.failureCallbacks[0]()
        mgr.preload()
        assertEquals(2, loader.loadCalls)
        assertEquals(1, loader.destroyCalls)
        loader.loadCallbacks[1](loader.ad())
        mgr.showAd(activity, {}, { fail("new ad should be ready") })
    }

    @Test fun `revocation during initialization prevents subsequent load`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val consent = FakeConsent()
        val gate = FakeInitializationGate(null)
        val mgr = manager(loader, gate, consent)
        mgr.preload()
        consent.update(false)
        gate.complete(true)
        assertEquals(0, loader.loadCalls)
    }

    @Test fun `stale reward callback after a privacy change cannot grant reward`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val consent = FakeConsent()
        val mgr = manager(loader, consent = consent)
        mgr.preload()
        var rewards = 0
        mgr.showAd(activity, { rewards++ }, { fail("ad should be ready") })
        consent.update(false)
        loader.pendingReward?.invoke()
        loader.pendingDismiss?.invoke()
        assertEquals(0, rewards)
        assertEquals(1, loader.loadCalls)
    }

    @Test fun `privacy preparation failure blocks real load and cached ad show`() {
        val loader = FakeLoader().apply { nextOutcome = FakeLoader.Outcome.LoadOk }
        val consent = FakeConsent().apply { privacyReady = false }
        val mgr = manager(loader, consent = consent)
        mgr.preload()
        assertEquals(0, loader.loadCalls)
        consent.privacyReady = true
        mgr.preload()
        assertEquals(1, loader.loadCalls)
        consent.privacyReady = false
        var unavailable = false
        mgr.showAd(activity, { fail("must not reward") }, { unavailable = true })
        assertTrue(unavailable)
        assertEquals(1, loader.destroyCalls)
        assertEquals(3, consent.preparations)
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)
}
