package com.moooo_works.letsgogps.data.repository

import com.moooo_works.letsgogps.data.billing.AdUnlockStore
import com.moooo_works.letsgogps.data.billing.BillingManager
import com.moooo_works.letsgogps.data.billing.FeatureCreditStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProRepositoryImplTest {

    private val createdScopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() {
        createdScopes.forEach { it.cancel() }
    }

    private fun build(
        subscribed: Boolean = false,
        expiry: MutableStateFlow<Long> = MutableStateFlow(0L),
        nowMillis: Long = 1_700_000_000_000L,
        credits: MutableStateFlow<Int> = MutableStateFlow(0),
    ): Triple<ProRepositoryImpl, BillingManager, MutableStateFlow<Long>> {
        val billing = mockk<BillingManager>(relaxed = true)
        every { billing.isProActive } returns MutableStateFlow(subscribed)
        val store = mockk<AdUnlockStore>(relaxed = true)
        every { store.expiryFlow } returns expiry
        coEvery { store.setExpiry(any()) } answers { expiry.value = firstArg() }

        val creditStore = mockk<FeatureCreditStore>(relaxed = true)
        every { creditStore.creditsFlow } returns credits
        every { creditStore.currentCredits() } returns credits.value
        coEvery { creditStore.add(any()) } answers { credits.value += firstArg<Int>() }
        coEvery { creditStore.consume(any()) } answers {
            val cost = firstArg<Int>()
            if (credits.value >= cost) { credits.value -= cost; true } else false
        }

        val dispatcher = UnconfinedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        createdScopes += scope

        val repo = ProRepositoryImpl(billing, store, creditStore).apply {
            clock = { nowMillis }
            tickerScope = scope
        }
        return Triple(repo, billing, expiry)
    }

    @Test
    fun `no subscription and no unlock - both inactive`() = runTest {
        val (repo, _, _) = build(subscribed = false)
        assertFalse(repo.isProActive.value)
        assertFalse(repo.isAdFreeActive.value)
    }

    @Test
    fun `unlock not yet expired - isProActive true, isAdFreeActive false`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, _) = build(
            subscribed = false,
            expiry = MutableStateFlow(now + 60_000),
            nowMillis = now,
        )
        assertTrue(repo.isProActive.value)
        assertFalse(repo.isAdFreeActive.value)
    }

    @Test
    fun `unlock already expired - isProActive false`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, _) = build(
            subscribed = false,
            expiry = MutableStateFlow(now - 1_000),
            nowMillis = now,
        )
        assertFalse(repo.isProActive.value)
    }

    @Test
    fun `subscription active - both true regardless of unlock`() = runTest {
        val (repo, _, _) = build(subscribed = true)
        assertTrue(repo.isProActive.value)
        assertTrue(repo.isAdFreeActive.value)
    }

    @Test
    fun `grantAdUnlockHours from zero adds 6h`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(subscribed = false, nowMillis = now)
        repo.grantAdUnlockHours(6)
        assertEquals(now + 6 * 3600_000L, expiry.value)
    }

    @Test
    fun `three consecutive grants total 18h`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(subscribed = false, nowMillis = now)
        repo.grantAdUnlockHours(6)
        repo.grantAdUnlockHours(6)
        repo.grantAdUnlockHours(6)
        assertEquals(now + 18 * 3600_000L, expiry.value)
    }

    @Test
    fun `fifth grant caps at 24h not 30h`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(subscribed = false, nowMillis = now)
        repeat(5) { repo.grantAdUnlockHours(6) }
        assertEquals(now + 24 * 3600_000L, expiry.value)
    }

    @Test
    fun `grant when expiry already past resets baseline to now`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(
            subscribed = false,
            expiry = MutableStateFlow(now - 10_000),
            nowMillis = now,
        )
        repo.grantAdUnlockHours(6)
        assertEquals(now + 6 * 3600_000L, expiry.value)
    }

    @Test
    fun `grant when 23h remaining caps at 24h`() = runTest {
        val now = 1_700_000_000_000L
        val (repo, _, expiry) = build(
            subscribed = false,
            expiry = MutableStateFlow(now + 23 * 3600_000L),
            nowMillis = now,
        )
        repo.grantAdUnlockHours(6)
        assertEquals(now + 24 * 3600_000L, expiry.value)
    }

    @Test
    fun `refreshProStatus delegates to billing manager`() = runTest {
        val (repo, billing, _) = build(subscribed = false)
        repo.refreshProStatus()
        coVerify { billing.queryActiveSubscriptions() }
    }

    // ── 次數錢包（計次制）──────────────────────────────────────────────────

    @Test
    fun `isSubscriptionActive 只反映訂閱不受 ad-unlock 影響`() = runTest {
        val expiry = MutableStateFlow(0L)
        val now = 1_700_000_000_000L
        val (repo, _, _) = build(subscribed = false, expiry = expiry, nowMillis = now)

        // 給一段還沒過期的 ad-unlock
        expiry.value = now + 6 * 3600_000L

        assertTrue("isProActive 應該被 ad-unlock 點亮", repo.isProActive.value)
        assertFalse("但 isSubscriptionActive 不該", repo.isSubscriptionActive.value)
    }

    @Test
    fun `isSubscriptionActive 在訂閱時為 true`() = runTest {
        val (repo, _, _) = build(subscribed = true)
        assertTrue(repo.isSubscriptionActive.value)
    }

    @Test
    fun `grantFeatureCredits 增加餘額`() = runTest {
        val credits = MutableStateFlow(0)
        val (repo, _, _) = build(credits = credits)

        repo.grantFeatureCredits(1)

        assertEquals(1, credits.value)
    }

    @Test
    fun `consumeFeatureCredits 餘額足夠時扣款並回傳 true`() = runTest {
        val credits = MutableStateFlow(2)
        val (repo, _, _) = build(credits = credits)

        assertTrue(repo.consumeFeatureCredits(1))
        assertEquals(1, credits.value)
    }

    @Test
    fun `consumeFeatureCredits 餘額不足時不扣款並回傳 false`() = runTest {
        val credits = MutableStateFlow(0)
        val (repo, _, _) = build(credits = credits)

        assertFalse(repo.consumeFeatureCredits(1))
        assertEquals("不足時一分都不能扣", 0, credits.value)
    }

    @Test
    fun `次數錢包與 ad-unlock 時數互不影響`() = runTest {
        val expiry = MutableStateFlow(0L)
        val credits = MutableStateFlow(0)
        val now = 1_700_000_000_000L
        val (repo, _, _) = build(expiry = expiry, nowMillis = now, credits = credits)

        repo.grantAdUnlockHours(6)
        assertEquals("給時數不得動到次數", 0, credits.value)

        repo.grantFeatureCredits(1)
        assertEquals("給次數不得動到時數", now + 6 * 3600_000L, expiry.value)
    }
}
