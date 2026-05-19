package com.moooo_works.letsgogps.data.repository

import com.moooo_works.letsgogps.data.billing.AdUnlockStore
import com.moooo_works.letsgogps.data.billing.BillingManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProRepositoryImplTest {

    private fun build(
        subscribed: Boolean = false,
        expiry: MutableStateFlow<Long> = MutableStateFlow(0L),
        nowMillis: Long = 1_700_000_000_000L,
        scope: TestScope
    ): Triple<ProRepositoryImpl, BillingManager, MutableStateFlow<Long>> {
        val billing = mockk<BillingManager>(relaxed = true)
        coEvery { billing.isProActive } returns MutableStateFlow(subscribed)
        val store = mockk<AdUnlockStore>(relaxed = true)
        coEvery { store.expiryFlow } returns expiry
        coEvery { store.setExpiry(any()) } answers { expiry.value = firstArg() }

        val repo = ProRepositoryImpl(billing, store).apply {
            clock = { nowMillis }
            tickerScope = scope
        }
        return Triple(repo, billing, expiry)
    }

    @Test
    fun `no subscription and no unlock - both inactive`() = runTest(StandardTestDispatcher()) {
        val (repo, _, _) = build(subscribed = false, scope = this)
        assertFalse(repo.isProActive.first())
        assertFalse(repo.isAdFreeActive.first())
    }

    @Test
    fun `unlock not yet expired - isProActive true, isAdFreeActive false`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, _) = build(
            subscribed = false,
            expiry = MutableStateFlow(now + 60_000),
            nowMillis = now,
            scope = this
        )
        assertTrue(repo.isProActive.first())
        assertFalse(repo.isAdFreeActive.first())
    }

    @Test
    fun `unlock already expired - isProActive false`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, _) = build(
            subscribed = false,
            expiry = MutableStateFlow(now - 1_000),
            nowMillis = now,
            scope = this
        )
        assertFalse(repo.isProActive.first())
    }

    @Test
    fun `subscription active - both true regardless of unlock`() = runTest(StandardTestDispatcher()) {
        val (repo, _, _) = build(subscribed = true, scope = this)
        assertTrue(repo.isProActive.first())
        assertTrue(repo.isAdFreeActive.first())
    }

    @Test
    fun `grantAdUnlockHours from zero adds 6h`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(subscribed = false, nowMillis = now, scope = this)
        repo.grantAdUnlockHours(6)
        assertEquals(now + 6 * 3600_000L, expiry.value)
    }

    @Test
    fun `three consecutive grants total 18h`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(subscribed = false, nowMillis = now, scope = this)
        repo.grantAdUnlockHours(6)
        repo.grantAdUnlockHours(6)
        repo.grantAdUnlockHours(6)
        assertEquals(now + 18 * 3600_000L, expiry.value)
    }

    @Test
    fun `fifth grant caps at 24h not 30h`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(subscribed = false, nowMillis = now, scope = this)
        repeat(5) { repo.grantAdUnlockHours(6) }
        assertEquals(now + 24 * 3600_000L, expiry.value)
    }

    @Test
    fun `grant when expiry already past resets baseline to now`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(
            subscribed = false,
            expiry = MutableStateFlow(now - 10_000),
            nowMillis = now,
            scope = this
        )
        repo.grantAdUnlockHours(6)
        assertEquals(now + 6 * 3600_000L, expiry.value)
    }

    @Test
    fun `grant when 23h remaining caps at 24h`() = runTest(StandardTestDispatcher()) {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(
            subscribed = false,
            expiry = MutableStateFlow(now + 23 * 3600_000L),
            nowMillis = now,
            scope = this
        )
        repo.grantAdUnlockHours(6)
        assertEquals(now + 24 * 3600_000L, expiry.value)
    }

    @Test
    fun `refreshProStatus delegates to billing manager`() = runTest(StandardTestDispatcher()) {
        val (repo, billing, _) = build(subscribed = false, scope = this)
        repo.refreshProStatus()
        coVerify { billing.queryActiveSubscriptions() }
    }
}
