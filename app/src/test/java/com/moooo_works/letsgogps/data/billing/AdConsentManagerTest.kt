package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class AdConsentManagerTest {
    private val activity = mockk<Activity>(relaxed = true)

    private class FakeBackend : ConsentBackend {
        override var canRequestAds = false
        override var privacyOptionsRequired = false
        var updateCalls = 0
        var formCalls = 0
        var privacyCalls = 0
        var privacyApplied = true
        var privacyApplyCalls = 0
        lateinit var success: () -> Unit
        lateinit var failure: (String) -> Unit
        lateinit var dismissed: (String?) -> Unit
        override fun update(activity: Activity, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
            updateCalls++
            success = onSuccess
            failure = onFailure
        }
        override fun showRequiredForm(activity: Activity, onDismiss: (String?) -> Unit) {
            formCalls++
            dismissed = onDismiss
        }
        override fun showPrivacyOptions(activity: Activity, onDismiss: (String?) -> Unit) {
            privacyCalls++
            dismissed = onDismiss
        }
        override fun applyMediationPrivacy(): Boolean {
            privacyApplyCalls++
            return privacyApplied
        }
    }

    @Test fun `first launch blocks ads until UMP finishes`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        assertFalse(manager.state.value.canRequestAds)
        manager.gatherConsent(activity)
        backend.canRequestAds = true
        backend.success()
        assertFalse(manager.state.value.canRequestAds)
        backend.dismissed(null)
        assertTrue(manager.state.value.canRequestAds)
        assertEquals(1, backend.privacyApplyCalls)
    }

    @Test fun `update failure uses UMP previous decision without assuming consent`() {
        for (allowed in listOf(false, true)) {
            val backend = FakeBackend()
            val manager = AdConsentManager(backend)
            manager.gatherConsent(activity)
            backend.canRequestAds = allowed
            backend.failure("offline")
            assertEquals(allowed, manager.state.value.canRequestAds)
            assertEquals("offline", manager.state.value.error)
            assertEquals(0, backend.formCalls)
        }
    }

    @Test fun `form error checks UMP instead of granting consent`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        backend.success()
        backend.dismissed("form failed")
        assertFalse(manager.state.value.canRequestAds)
        assertFalse(manager.state.value.busy)
    }

    @Test fun `duplicate callbacks and activity recreation do not show duplicate forms`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        manager.gatherConsent(activity)
        assertEquals(1, backend.updateCalls)
        backend.success()
        backend.success()
        assertEquals(1, backend.formCalls)
        backend.canRequestAds = true
        backend.dismissed(null)
        backend.failure("late failure")
        manager.gatherConsent(mockk(relaxed = true))
        assertEquals(1, backend.updateCalls)
        assertTrue(manager.state.value.canRequestAds)
        assertNull(manager.state.value.error)
    }

    @Test fun `privacy options pause requests and invalidate previous ads even if still allowed`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        backend.canRequestAds = true
        backend.privacyOptionsRequired = true
        backend.success()
        backend.dismissed(null)
        val previousRevision = manager.state.value.revision
        assertTrue(manager.state.value.privacyOptionsRequired)
        manager.showPrivacyOptions(activity)
        assertFalse(manager.state.value.canRequestAds)
        assertTrue(manager.state.value.revision > previousRevision)
        manager.showPrivacyOptions(activity)
        assertEquals(1, backend.privacyCalls)
        backend.dismissed(null)
        assertTrue(manager.state.value.canRequestAds)
    }

    @Test fun `privacy entry is not usable unless UMP requires it`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.showPrivacyOptions(activity)
        assertEquals(0, backend.privacyCalls)
    }

    @Test fun `new activity supersedes unfinished old request`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        val oldSuccess = backend.success
        val replacement = mockk<Activity>(relaxed = true)
        manager.gatherConsent(replacement)
        oldSuccess()
        assertEquals(0, backend.formCalls)
        backend.success()
        assertEquals(1, backend.formCalls)
    }

    @Test fun `destroyed activity cannot launch a form`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        every { activity.isDestroyed } returns true
        backend.canRequestAds = true
        backend.success()
        assertEquals(0, backend.formCalls)
        assertFalse(manager.state.value.canRequestAds)
    }

    @Test fun `mediation privacy failure never permits ads even when UMP allows them`() {
        val backend = FakeBackend().apply { privacyApplied = false }
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        backend.canRequestAds = true
        backend.success()
        backend.dismissed(null)
        assertFalse(manager.state.value.canRequestAds)
        assertNotNull(manager.state.value.error)
    }

    @Test fun `destroyed privacy form owner allows replacement activity to recover`() {
        val backend = FakeBackend()
        val manager = AdConsentManager(backend)
        manager.gatherConsent(activity)
        backend.canRequestAds = true
        backend.privacyOptionsRequired = true
        backend.success()
        backend.dismissed(null)
        manager.showPrivacyOptions(activity)
        every { activity.isDestroyed } returns true
        backend.dismissed(null)
        assertFalse(manager.state.value.canRequestAds)
        manager.gatherConsent(mockk(relaxed = true))
        assertEquals(2, backend.updateCalls)
    }
}
