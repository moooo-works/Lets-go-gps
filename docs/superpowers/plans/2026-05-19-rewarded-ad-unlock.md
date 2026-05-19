# Rewarded Ad 6h Pro Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "watch one rewarded ad → 6 hours of Pro features" path stackable up to 24h, while keeping the banner on the map for non-subscribers and completely removing the existing "interstitial on start mocking" mechanism.

**Architecture:** `ProRepository` gains a second `isAdFreeActive` StateFlow (subscription-only) alongside the combined `isProActive` (subscription OR ad-unlock). Ad-unlock expiry persists in a dedicated DataStore and is capped at 24h on grant. `RewardedAdManager` is a Hilt singleton shared between Settings and ProUpgradeDialog. The old `InterstitialAdManager` and all its references are deleted.

**Tech Stack:** Kotlin 1.9.22, Hilt 2.48, AndroidX DataStore-preferences, AdMob (`com.google.android.gms.ads`), MockK, kotlinx-coroutines-test, Robolectric SDK 33.

**Spec:** `docs/superpowers/specs/2026-05-19-rewarded-ad-unlock-design.md`

---

## File Structure

### New

| Path | Responsibility |
|------|----------------|
| `app/src/main/java/com/moooo_works/letsgogps/data/billing/AdUnlockStore.kt` | DataStore wrapper for `pro_ad_unlock_expiry_millis` (Long, epoch ms) |
| `app/src/main/java/com/moooo_works/letsgogps/data/billing/RewardedAdManager.kt` | Hilt `@Singleton`; preload + show + reward callback |
| `app/src/main/java/com/moooo_works/letsgogps/ui/settings/ProSection.kt` | Composable for Settings page top Pro section (3 states) |
| `app/src/test/java/com/moooo_works/letsgogps/data/billing/AdUnlockStoreTest.kt` | Roundtrip persist/read test |
| `app/src/test/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImplTest.kt` | combine logic, 24h cap, expiry |

### Modified

| Path | What changes |
|------|--------------|
| `domain/repository/ProRepository.kt` | +`isAdFreeActive`, +`adUnlockExpiryMillis`, +`grantAdUnlockHours` |
| `data/repository/ProRepositoryImpl.kt` | combine with AdUnlockStore + clock + 60s ticker; implement grant with 24h cap |
| `app/build.gradle.kts` | Remove `INTERSTITIAL_AD_UNIT_ID`; add `REWARDED_AD_UNIT_ID`; release Banner ID → new account |
| `app/src/main/AndroidManifest.xml` | APPLICATION_ID → new account `~2219581212` |
| `ui/map/MapState.kt` | +`isAdFreeActive: Boolean = false` |
| `ui/map/MapViewModel.kt` | Collect `proRepository.isAdFreeActive`; expose grant via new lambda for dialog |
| `ui/map/MapScreen.kt:87` | Remove `interstitialAdManager` instantiation |
| `ui/map/MapScreen.kt:408` | Banner gate: `isProActive` → `isAdFreeActive` |
| `ui/map/MapScreen.kt:443` | ProUpgradeDialog 新增 `onWatchAd` + `watchAdEnabled` |
| `ui/map/MapBottomPanel.kt:71,135` | Remove `interstitialAdManager` param; directly call `onStartMocking` |
| `ui/pro/ProUpgradeDialog.kt` | New signature: `onWatchAd`, `onSubscribe` (renamed), `watchAdEnabled` |
| `ui/settings/SettingsViewModel.kt` | +`proSection: StateFlow<ProSectionState>`, +`watchRewardedAd(activity)`, ticker for remaining |
| `ui/settings/SettingsScreen.kt` | Mount `ProSection` at top |
| `app/src/test/.../SettingsViewModelTest.kt` | Mock new ProRepository surface; test grant callback; test remaining formatting |
| `app/src/test/.../MapViewModelTest.kt` | Mock `proRepository.isAdFreeActive = MutableStateFlow(false)` |
| `CLAUDE.md` | Add monetization section, update Pro flag description |

### Deleted

| Path | Reason |
|------|--------|
| `app/src/main/java/com/moooo_works/letsgogps/ui/ads/InterstitialAdManager.kt` | Interstitial-on-start-mocking mechanism removed |

Totals: 5 new + 14 modified + 1 deleted = 20 files.

---

## Task 1: AdUnlockStore (DataStore wrapper)

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/data/billing/AdUnlockStore.kt`
- Test: `app/src/test/java/com/moooo_works/letsgogps/data/billing/AdUnlockStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/moooo_works/letsgogps/data/billing/AdUnlockStoreTest.kt
package com.moooo_works.letsgogps.data.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdUnlockStoreTest {

    private lateinit var context: Context
    private lateinit var store: AdUnlockStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = AdUnlockStore(context)
    }

    @After
    fun tearDown() {
        // Clean up DataStore file between tests so each test starts fresh.
        File(context.filesDir, "datastore/ad_unlock.preferences_pb").delete()
    }

    @Test
    fun `default expiry is zero`() = runTest {
        assertEquals(0L, store.expiryFlow.first())
    }

    @Test
    fun `setExpiry persists value`() = runTest {
        store.setExpiry(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, store.expiryFlow.first())
    }

    @Test
    fun `setExpiry overwrites previous value`() = runTest {
        store.setExpiry(1_000L)
        store.setExpiry(2_000L)
        assertEquals(2_000L, store.expiryFlow.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.data.billing.AdUnlockStoreTest"
```
Expected: `Unresolved reference: AdUnlockStore` compile error.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/data/billing/AdUnlockStore.kt
package com.moooo_works.letsgogps.data.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.adUnlockDataStore by preferencesDataStore(name = "ad_unlock")

@Singleton
class AdUnlockStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val expiryKey = longPreferencesKey("pro_ad_unlock_expiry_millis")

    val expiryFlow: Flow<Long> = context.adUnlockDataStore.data.map { it[expiryKey] ?: 0L }

    suspend fun setExpiry(millis: Long) {
        context.adUnlockDataStore.edit { it[expiryKey] = millis }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.data.billing.AdUnlockStoreTest"
```
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/data/billing/AdUnlockStore.kt \
        app/src/test/java/com/moooo_works/letsgogps/data/billing/AdUnlockStoreTest.kt
git commit -m "feat: add AdUnlockStore for rewarded-ad expiry persistence"
```

---

## Task 2: RewardedAdManager

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/data/billing/RewardedAdManager.kt`
- Test: `app/src/test/java/com/moooo_works/letsgogps/data/billing/RewardedAdManagerTest.kt`

The AdMob `RewardedAd` class is final and hard to mock. Approach: inject a `RewardedAdLoader` interface so the test can use a fake loader; production uses real `RewardedAd.load`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/moooo_works/letsgogps/data/billing/RewardedAdManagerTest.kt
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.data.billing.RewardedAdManagerTest"
```
Expected: compile errors (RewardedAdManager class not defined).

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/data/billing/RewardedAdManager.kt
package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.moooo_works.letsgogps.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManager(
    private val loader: RewardedAdLoader,
    private val unitId: String
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        loader = AdMobRewardedAdLoader(context),
        unitId = BuildConfig.REWARDED_AD_UNIT_ID
    )

    private var loadedAd: LoadedAd? = null
    private var isLoading = false

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        loader.load(
            unitId = unitId,
            onLoaded = { ad ->
                loadedAd = ad
                isLoading = false
            },
            onFailed = {
                loadedAd = null
                isLoading = false
            }
        )
    }

    fun showAd(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = loadedAd
        if (ad == null) {
            onUnavailable()
            preload()
            return
        }
        loadedAd = null
        var rewarded = false
        ad.show(
            activity = activity,
            onReward = { rewarded = true },
            onDismiss = {
                if (rewarded) onReward()
                preload()
            }
        )
    }

    interface RewardedAdLoader {
        fun load(unitId: String, onLoaded: (LoadedAd) -> Unit, onFailed: () -> Unit)
    }

    interface LoadedAd {
        fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit)
    }

    private class AdMobRewardedAdLoader(private val context: Context) : RewardedAdLoader {
        override fun load(unitId: String, onLoaded: (LoadedAd) -> Unit, onFailed: () -> Unit) {
            RewardedAd.load(
                context,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        onLoaded(AdMobLoadedAd(ad))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        onFailed()
                    }
                }
            )
        }
    }

    private class AdMobLoadedAd(private val ad: RewardedAd) : LoadedAd {
        override fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { onDismiss() }
            }
            ad.show(activity, OnUserEarnedRewardListener { _ -> onReward() })
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.data.billing.RewardedAdManagerTest"
```
Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/data/billing/RewardedAdManager.kt \
        app/src/test/java/com/moooo_works/letsgogps/data/billing/RewardedAdManagerTest.kt
git commit -m "feat: add RewardedAdManager with loader abstraction for tests"
```

> NOTE: The code references `BuildConfig.REWARDED_AD_UNIT_ID` which is added in Task 4. Compile succeeds because the Hilt `@Inject constructor` defers reading the field to runtime — the symbol is resolved when Task 4 lands. If a strict build catches this earlier, do Task 4 before Task 2; the test in Task 2 does not depend on BuildConfig.

---

## Task 3: ProRepository interface extension + impl + tests

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/domain/repository/ProRepository.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImpl.kt`
- Create: `app/src/test/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImplTest.kt`
- Modify: `app/src/test/java/com/moooo_works/letsgogps/ui/settings/SettingsViewModelTest.kt` (existing fake ProRepository)
- Modify: `app/src/test/java/com/moooo_works/letsgogps/ui/map/MapViewModelTest.kt` (existing fake ProRepository)

- [ ] **Step 1: Update the interface**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/domain/repository/ProRepository.kt
package com.moooo_works.letsgogps.domain.repository

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface ProRepository {
    /** Subscription OR active ad-unlock (used to gate Pro features). */
    val isProActive: StateFlow<Boolean>

    /** Subscription only (used to gate ad display — banner / interstitial). */
    val isAdFreeActive: StateFlow<Boolean>

    /** Current ad-unlock expiry epoch millis; 0 means not unlocked. */
    val adUnlockExpiryMillis: StateFlow<Long>

    suspend fun refreshProStatus()

    /** Grant N hours of ad-unlocked Pro; stacks onto existing expiry, capped at 24h total from now. */
    suspend fun grantAdUnlockHours(hours: Long)

    fun launchBillingFlow(activity: Activity)
}
```

- [ ] **Step 2: Write the failing impl test**

```kotlin
// app/src/test/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImplTest.kt
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
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.data.repository.ProRepositoryImplTest"
```
Expected: compile errors — interface now has new members not implemented in `ProRepositoryImpl`.

- [ ] **Step 4: Implement ProRepositoryImpl**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImpl.kt
package com.moooo_works.letsgogps.data.repository

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.moooo_works.letsgogps.data.billing.AdUnlockStore
import com.moooo_works.letsgogps.data.billing.BillingManager
import com.moooo_works.letsgogps.domain.repository.ProRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProRepositoryImpl @Inject constructor(
    private val billingManager: BillingManager,
    private val adUnlockStore: AdUnlockStore,
) : ProRepository {

    @VisibleForTesting
    internal var clock: () -> Long = { System.currentTimeMillis() }

    @VisibleForTesting
    internal var tickerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val nowFlow = flow {
        while (true) {
            emit(clock())
            delay(60_000)
        }
    }

    override val adUnlockExpiryMillis: StateFlow<Long> by lazy {
        adUnlockStore.expiryFlow.stateIn(tickerScope, SharingStarted.Eagerly, 0L)
    }

    override val isAdFreeActive: StateFlow<Boolean> by lazy {
        billingManager.isProActive
    }

    override val isProActive: StateFlow<Boolean> by lazy {
        combine(
            billingManager.isProActive,
            adUnlockStore.expiryFlow,
            nowFlow
        ) { subscribed, expiry, now -> subscribed || expiry > now }
            .stateIn(tickerScope, SharingStarted.Eagerly, false)
    }

    override suspend fun refreshProStatus() {
        billingManager.queryActiveSubscriptions()
    }

    override suspend fun grantAdUnlockHours(hours: Long) {
        val nowMillis = clock()
        val current = adUnlockExpiryMillis.value
        val newExpiry = maxOf(current, nowMillis) + hours * 3600_000L
        val cap = nowMillis + 24 * 3600_000L
        adUnlockStore.setExpiry(minOf(newExpiry, cap))
    }

    override fun launchBillingFlow(activity: Activity) {
        billingManager.launchBillingFlow(activity)
    }
}
```

- [ ] **Step 5: Update existing test fakes for new interface**

`MapViewModelTest.kt`: find any mock of `ProRepository` and add:

```kotlin
every { proRepository.isAdFreeActive } returns MutableStateFlow(false)
every { proRepository.adUnlockExpiryMillis } returns MutableStateFlow(0L)
coEvery { proRepository.grantAdUnlockHours(any()) } returns Unit
```

`SettingsViewModelTest.kt`: same additions on `proRepository` mock (around line 80).

- [ ] **Step 6: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: green. ProRepositoryImplTest's 10 assertions pass; existing tests still pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/domain/repository/ProRepository.kt \
        app/src/main/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImpl.kt \
        app/src/test/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImplTest.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/map/MapViewModelTest.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: extend ProRepository with isAdFreeActive and grantAdUnlockHours"
```

---

## Task 4: BuildConfig + AndroidManifest migration to new AdMob account

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml:41`

- [ ] **Step 1: Update build.gradle.kts**

Replace the buildTypes block's ad-ID lines:

```kotlin
// Inside debug { ... }
buildConfigField("Boolean", "DEV_FORCE_PRO", "true")
buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
// (No INTERSTITIAL_AD_UNIT_ID — removed)
```

```kotlin
// Inside release { ... }
buildConfigField("Boolean", "DEV_FORCE_PRO", "false")
buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-7328056144057376/1824598031\"")
buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-7328056144057376/6473078035\"")
// (No INTERSTITIAL_AD_UNIT_ID — removed)
```

- [ ] **Step 2: Update AndroidManifest.xml line 41**

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-7328056144057376~2219581212" />
```

- [ ] **Step 3: Verify gradle sync**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. `BuildConfig.REWARDED_AD_UNIT_ID` resolves; `BuildConfig.INTERSTITIAL_AD_UNIT_ID` references will fail if anything still uses it (deal with that in Task 5).

If Task 5 hasn't been done yet and you see `Unresolved reference: INTERSTITIAL_AD_UNIT_ID` errors, that's expected — proceed to Task 5.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: migrate to new AdMob account and add REWARDED_AD_UNIT_ID"
```

---

## Task 5: Remove the interstitial-on-start-mocking mechanism

**Files:**
- Delete: `app/src/main/java/com/moooo_works/letsgogps/ui/ads/InterstitialAdManager.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt:87` (remove instantiation)
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt:415` (remove from MapBottomPanel call)
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapBottomPanel.kt:71,135`

- [ ] **Step 1: Remove from `MapBottomPanel.kt`**

Delete the `interstitialAdManager: InterstitialAdManager,` parameter (line 71). At line 135, replace:

```kotlin
interstitialAdManager.showAd(activity) { onStartMocking() }
```

with:

```kotlin
onStartMocking()
```

Also remove the now-unused import of `InterstitialAdManager`.

- [ ] **Step 2: Remove from `MapScreen.kt`**

Delete line 87:

```kotlin
val interstitialAdManager = remember { InterstitialAdManager(context) }
```

In the `MapBottomPanel(...)` call (around line 412–415), remove the `interstitialAdManager = interstitialAdManager,` argument.

Also remove the now-unused import of `InterstitialAdManager`.

- [ ] **Step 3: Delete InterstitialAdManager.kt**

```bash
rm app/src/main/java/com/moooo_works/letsgogps/ui/ads/InterstitialAdManager.kt
```

- [ ] **Step 4: Build to verify nothing else references it**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. If a stale reference appears, grep and delete it:

```bash
grep -rn "InterstitialAdManager\|INTERSTITIAL_AD_UNIT_ID" app/src/main app/src/test
```
Expected output: empty.

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/ads/InterstitialAdManager.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapBottomPanel.kt
git commit -m "refactor: remove interstitial-on-start-mocking mechanism"
```

---

## Task 6: Wire `isAdFreeActive` through Map layer (banner gate)

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapState.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt:191–192`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt:408`

- [ ] **Step 1: Add `isAdFreeActive` to MapUiState**

In `MapState.kt`, add a new field after `isProActive: Boolean = false,`:

```kotlin
val isProActive: Boolean = false,
val isAdFreeActive: Boolean = false,
val showProUpgrade: Boolean = false,
```

- [ ] **Step 2: Collect `isAdFreeActive` in MapViewModel**

In `MapViewModel.kt` around line 188–193, where `proRepository.isProActive` is collected, add a parallel collection. Look for:

```kotlin
viewModelScope.launch {
    proRepository.isProActive.collect { isPro ->
        _uiState.update { it.copy(isProActive = isPro) }
    }
}
```

Add immediately after:

```kotlin
viewModelScope.launch {
    proRepository.isAdFreeActive.collect { isAdFree ->
        _uiState.update { it.copy(isAdFreeActive = isAdFree) }
    }
}
```

- [ ] **Step 3: Change banner gate in MapScreen**

At line 408 in `MapScreen.kt`:

```kotlin
// before
if (!uiState.isProActive) {
    BannerAdView()
}
// after
if (!uiState.isAdFreeActive) {
    BannerAdView()
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: green. The `MapViewModelTest.kt` mock from Task 3 should already return `MutableStateFlow(false)` for `isAdFreeActive`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/map/MapState.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt
git commit -m "feat: split feature gating from ad-free gating in map UI"
```

---

## Task 7: ProUpgradeDialog signature + map call site wiring

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/pro/ProUpgradeDialog.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt:443`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt` (new `watchRewardedAd` fn)

- [ ] **Step 1: Update ProUpgradeDialog**

Replace the body of `ProUpgradeDialog.kt`:

```kotlin
package com.moooo_works.letsgogps.ui.pro

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R

@Composable
fun ProUpgradeDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
    watchAdEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(stringResource(R.string.pro_dialog_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pro_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    stringResource(R.string.pro_dialog_feature_routes),
                    stringResource(R.string.pro_dialog_feature_joystick),
                    stringResource(R.string.pro_dialog_feature_export),
                    stringResource(R.string.pro_dialog_feature_ads)
                ).forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    stringResource(R.string.pro_dialog_cancel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onWatchAd,
                    enabled = watchAdEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pro_dialog_action_watch_ad))
                }
                Button(
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pro_dialog_action_upgrade))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.map_action_cancel))
            }
        }
    )
}
```

- [ ] **Step 2: Add new string resources**

In `app/src/main/res/values/strings.xml` (and any locale files that already translate `pro_dialog_action_upgrade`), add:

```xml
<string name="pro_dialog_action_watch_ad">看一支廣告解鎖 6 小時</string>
```

For other locales, translate similarly (English: "Watch one ad to unlock 6 hours", etc.). If the team can't translate immediately, English as fallback is acceptable for now.

- [ ] **Step 3: Inject RewardedAdManager into MapViewModel and add `watchRewardedAd`**

In `MapViewModel.kt` constructor parameters, add (Hilt will inject automatically since it's `@Singleton`):

```kotlin
private val rewardedAdManager: com.moooo_works.letsgogps.data.billing.RewardedAdManager,
```

In `init { ... }`, after existing collectors, call:

```kotlin
rewardedAdManager.preload()
```

Add a new public function near `launchBillingFlow`:

```kotlin
fun watchRewardedAd(activity: Activity) {
    rewardedAdManager.showAd(
        activity = activity,
        onReward = {
            viewModelScope.launch {
                proRepository.grantAdUnlockHours(6)
                _uiState.update { it.copy(showProUpgrade = false) }
            }
        },
        onUnavailable = {
            _uiState.update { it.copy(mockError = MockError.RewardedAdUnavailable) }
        }
    )
}
```

- [ ] **Step 4: Add `MockError.RewardedAdUnavailable`**

In `MockError.kt`, add:

```kotlin
object RewardedAdUnavailable : MockError()
```

And in the consumer that maps `MockError` to user-facing text, add a case (look for the existing `when` switch on `MockError`):

```xml
<!-- strings.xml -->
<string name="mock_error_rewarded_unavailable">暫時沒有可用廣告，請稍後再試</string>
```

- [ ] **Step 5: Wire `MapScreen` dialog call site**

At line 443 in `MapScreen.kt`:

```kotlin
if (uiState.showProUpgrade) {
    ProUpgradeDialog(
        onDismiss = { viewModel.dismissProUpgrade() },
        onWatchAd = { activity?.let { viewModel.watchRewardedAd(it) } },
        onSubscribe = { activity?.let { viewModel.launchBillingFlow(it) } ?: viewModel.dismissProUpgrade() },
        watchAdEnabled = uiState.adUnlockRemainingMillis < 18 * 3600_000L,
    )
}
```

For `adUnlockRemainingMillis`: add to `MapUiState`:

```kotlin
val adUnlockRemainingMillis: Long = 0L,
```

And in `MapViewModel`'s `proRepository.adUnlockExpiryMillis` collector (new collector):

```kotlin
viewModelScope.launch {
    proRepository.adUnlockExpiryMillis.collect { expiry ->
        _uiState.update { it.copy(adUnlockRemainingMillis = (expiry - System.currentTimeMillis()).coerceAtLeast(0L)) }
    }
}
```

> NOTE: This recomputes only on `expiry` changes, so the value goes stale until the next change. For dialog UX this is acceptable (the worst case is the dialog showing the button enabled when it should be disabled near the 18h boundary; the post-grant cap in `ProRepositoryImpl` still enforces the 24h ceiling). A precise per-minute tick lives in Settings (Task 9).

- [ ] **Step 6: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: green. May need to update `MapViewModelTest` constructor call site to pass a mocked `RewardedAdManager`:

```kotlin
private val rewardedAdManager = mockk<RewardedAdManager>(relaxed = true)
// ... in viewModel = MapViewModel(..., rewardedAdManager, ...)
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/pro/ProUpgradeDialog.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapScreen.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapState.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MockError.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/moooo_works/letsgogps/ui/map/MapViewModelTest.kt
git commit -m "feat: add watch-ad option to ProUpgradeDialog and wire through MapViewModel"
```

---

## Task 8: SettingsViewModel Pro section state

**Files:**
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/moooo_works/letsgogps/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Define the ProSectionState data shape**

Add inside `SettingsViewModel.kt` (above the class):

```kotlin
sealed interface ProSectionState {
    object Free : ProSectionState
    data class AdUnlocked(val remainingMillis: Long, val watchAdEnabled: Boolean) : ProSectionState
    object Subscribed : ProSectionState
}
```

- [ ] **Step 2: Write failing test for the three states**

In `SettingsViewModelTest.kt`, add:

```kotlin
@Test
fun `proSection - Free when no subscription and no unlock`() = runTest {
    every { proRepository.isProActive } returns MutableStateFlow(false)
    every { proRepository.isAdFreeActive } returns MutableStateFlow(false)
    every { proRepository.adUnlockExpiryMillis } returns MutableStateFlow(0L)
    val vm = buildViewModel()
    assertEquals(ProSectionState.Free, vm.proSection.value)
}

@Test
fun `proSection - Subscribed when isAdFreeActive`() = runTest {
    every { proRepository.isProActive } returns MutableStateFlow(true)
    every { proRepository.isAdFreeActive } returns MutableStateFlow(true)
    every { proRepository.adUnlockExpiryMillis } returns MutableStateFlow(0L)
    val vm = buildViewModel()
    assertEquals(ProSectionState.Subscribed, vm.proSection.value)
}

@Test
fun `proSection - AdUnlocked watchAdEnabled false when remaining over 18h`() = runTest {
    val now = System.currentTimeMillis()
    every { proRepository.isProActive } returns MutableStateFlow(true)
    every { proRepository.isAdFreeActive } returns MutableStateFlow(false)
    every { proRepository.adUnlockExpiryMillis } returns MutableStateFlow(now + 20 * 3600_000L)
    val vm = buildViewModel()
    val state = vm.proSection.value as ProSectionState.AdUnlocked
    assertFalse(state.watchAdEnabled)
}

@Test
fun `watchRewardedAd reward callback calls grantAdUnlockHours(6)`() = runTest {
    val activity = mockk<android.app.Activity>(relaxed = true)
    val rewardedAdManager = mockk<RewardedAdManager>(relaxed = true)
    val rewardSlot = slot<() -> Unit>()
    every { rewardedAdManager.showAd(any(), capture(rewardSlot), any()) } answers {}
    every { proRepository.isProActive } returns MutableStateFlow(false)
    every { proRepository.isAdFreeActive } returns MutableStateFlow(false)
    every { proRepository.adUnlockExpiryMillis } returns MutableStateFlow(0L)
    coEvery { proRepository.grantAdUnlockHours(any()) } returns Unit

    val vm = buildViewModel(rewardedAdManager = rewardedAdManager)
    vm.watchRewardedAd(activity)
    rewardSlot.captured.invoke()
    advanceUntilIdle()

    coVerify { proRepository.grantAdUnlockHours(6) }
}

// Helper — adjust signature to match existing `buildViewModel` in the file or
// add it. Pass `rewardedAdManager` as an optional override.
private fun buildViewModel(
    rewardedAdManager: RewardedAdManager = mockk(relaxed = true)
): SettingsViewModel = SettingsViewModel(
    locationRepository,
    mockStateRepository,
    settingsRepository,
    mockEngine,
    proRepository,
    systemHealthCheck,
    rewardedAdManager,        // new
    context
)
```

> NOTE: If `SettingsViewModelTest` currently constructs the VM inline in `setup()`, refactor that into a `buildViewModel()` helper as part of this step so the new tests can build VMs with custom state.

- [ ] **Step 3: Run tests to confirm they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.ui.settings.SettingsViewModelTest"
```
Expected: compile / runtime errors — `ProSectionState`, `proSection`, `watchRewardedAd`, and `rewardedAdManager` constructor param don't exist yet.

- [ ] **Step 4: Implement in SettingsViewModel**

Inject `RewardedAdManager` in the constructor:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val settingsRepository: SettingsRepository,
    private val mockEngine: LocationMockEngine,
    private val proRepository: ProRepository,
    private val systemHealthCheck: SystemHealthCheck,
    private val rewardedAdManager: com.moooo_works.letsgogps.data.billing.RewardedAdManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    // ...existing fields...
```

Add a 60-second ticker StateFlow inside the class:

```kotlin
private val nowTick: StateFlow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        kotlinx.coroutines.delay(60_000)
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
```

Add `proSection` derived StateFlow:

```kotlin
val proSection: StateFlow<ProSectionState> = combine(
    proRepository.isAdFreeActive,
    proRepository.adUnlockExpiryMillis,
    nowTick
) { adFree, expiry, now ->
    when {
        adFree -> ProSectionState.Subscribed
        expiry > now -> {
            val remaining = expiry - now
            ProSectionState.AdUnlocked(
                remainingMillis = remaining,
                watchAdEnabled = remaining < 18 * 3600_000L
            )
        }
        else -> ProSectionState.Free
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProSectionState.Free)
```

Add `watchRewardedAd`:

```kotlin
fun watchRewardedAd(activity: Activity, onUnavailable: () -> Unit = {}) {
    rewardedAdManager.showAd(
        activity = activity,
        onReward = {
            viewModelScope.launch {
                proRepository.grantAdUnlockHours(6)
            }
        },
        onUnavailable = onUnavailable
    )
}
```

Preload in `init { ... }` (currently calls `refreshMockPermission()`):

```kotlin
init {
    refreshMockPermission()
    rewardedAdManager.preload()
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.moooo_works.letsgogps.ui.settings.SettingsViewModelTest"
```
Expected: 4 new tests pass, existing pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/settings/SettingsViewModel.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add ProSectionState and watchRewardedAd to SettingsViewModel"
```

---

## Task 9: ProSection composable + SettingsScreen integration

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/ui/settings/ProSection.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (+locale files)

- [ ] **Step 1: Add string resources**

In `strings.xml`:

```xml
<string name="settings_pro_section_title">Pro 訂閱</string>
<string name="settings_pro_free_card_title">看廣告解鎖 6 小時</string>
<string name="settings_pro_free_card_subtitle">一次廣告 = 6 小時 Pro 功能</string>
<string name="settings_pro_free_card_button">看廣告</string>
<string name="settings_pro_subscribe_card_title">訂閱無廣告版</string>
<string name="settings_pro_subscribe_card_subtitle">$3.99 / 月，永久 Pro 且無廣告</string>
<string name="settings_pro_subscribe_card_button">訂閱</string>
<string name="settings_pro_unlocked_title">✨ Pro 解鎖中</string>
<string name="settings_pro_unlocked_remaining">剩 %1$s</string>
<string name="settings_pro_unlocked_watch_more">再看一支 +6h</string>
<string name="settings_pro_unlocked_cap_note">累積上限 24 小時</string>
<string name="settings_pro_unlocked_upgrade">升級訂閱（永久無廣告）</string>
<string name="settings_pro_subscribed_title">✅ Pro 訂閱中</string>
<string name="settings_pro_subscribed_manage">管理訂閱</string>
<string name="settings_pro_unavailable">暫時沒有可用廣告，請稍後再試</string>
<string name="settings_pro_unlock_extended">+6 小時解鎖成功</string>
```

- [ ] **Step 2: Create ProSection composable**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/ui/settings/ProSection.kt
package com.moooo_works.letsgogps.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R

@Composable
fun ProSection(
    state: ProSectionState,
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.settings_pro_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            when (state) {
                ProSectionState.Free -> ProFreeContent(onWatchAd, onSubscribe)
                is ProSectionState.AdUnlocked -> ProUnlockedContent(state, onWatchAd, onSubscribe)
                ProSectionState.Subscribed -> ProSubscribedContent(onManageSubscription)
            }
        }
    }
}

@Composable
private fun ProFreeContent(onWatchAd: () -> Unit, onSubscribe: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.weight(1f).padding(0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_pro_free_card_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_pro_free_card_subtitle), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_pro_free_card_button))
                }
            }
        }
        Card(modifier = Modifier.weight(1f).padding(0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_pro_subscribe_card_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_pro_subscribe_card_subtitle), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_pro_subscribe_card_button))
                }
            }
        }
    }
}

@Composable
private fun ProUnlockedContent(
    state: ProSectionState.AdUnlocked,
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_pro_unlocked_title), fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.settings_pro_unlocked_remaining, formatRemaining(state.remainingMillis)),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { (state.remainingMillis.toFloat() / (24f * 3600_000f)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onWatchAd,
                enabled = state.watchAdEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_pro_unlocked_watch_more))
            }
            OutlinedButton(onClick = onSubscribe, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_pro_unlocked_upgrade))
            }
        }
        if (!state.watchAdEnabled) {
            Text(stringResource(R.string.settings_pro_unlocked_cap_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProSubscribedContent(onManageSubscription: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_pro_subscribed_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onManageSubscription) {
            Text(stringResource(R.string.settings_pro_subscribed_manage))
        }
    }
}

internal fun formatRemaining(millis: Long): String {
    val totalMinutes = millis / 60_000
    return when {
        totalMinutes >= 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        totalMinutes >= 1 -> "${totalMinutes}m"
        else -> "不到 1 分鐘"
    }
}
```

- [ ] **Step 3: Mount ProSection at top of SettingsScreen**

In `SettingsScreen.kt`, near the top of the scrollable content (inside the `Column` / `LazyColumn` that hosts existing sections), insert:

```kotlin
val proSection by viewModel.proSection.collectAsState()
val activity = LocalContext.current as? android.app.Activity

ProSection(
    state = proSection,
    onWatchAd = {
        activity?.let { viewModel.watchRewardedAd(it) }
    },
    onSubscribe = {
        activity?.let { viewModel.launchBillingFlow(it) }
    },
    onManageSubscription = {
        // Launch Play Store subscription management
        val url = "https://play.google.com/store/account/subscriptions?sku=mockgps_pro_monthly&package=${context.packageName}"
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }
    },
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
```

> NOTE: If `SettingsScreen.kt` uses a different layout structure (e.g., manual sections), insert the `ProSection(...)` call at the appropriate top-of-list location. The exact insertion point depends on the existing scrolling-container pattern. Read the file before this step and find the right line.

- [ ] **Step 4: Build and run tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
Expected: all green. Lint may flag missing translations for new strings — translate the most-used locales (en, ja, ko, zh-rTW, zh-rCN) or accept fallback to default locale for now.

- [ ] **Step 5: Smoke test on emulator / device**

Install the debug build and verify:

1. Free user sees two side-by-side cards in Settings
2. Tap "看廣告" → Google test rewarded video plays → after dismiss, Settings shows "✨ Pro 解鎖中" with progress bar
3. Open MapScreen → joystick / route features are unlocked
4. Banner still visible on MapScreen
5. Starting mocking no longer triggers an interstitial
6. Re-watch ad → remaining grows up to 24h cap
7. Toggle subscription via dev flag `BuildConfig.DEV_FORCE_PRO = true` (or actual subscription test) → ProSection shows "✅ Pro 訂閱中" and banner disappears

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/settings/ProSection.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat: add Pro section to Settings page with three states"
```

---

## Task 10: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update monetization section**

Replace the existing line:

```
**變現**：AdMob（橫幅 + 插頁式廣告）+ Google Play Billing（月繳訂閱，ID：`mockgps_pro_monthly`）。
```

with:

```
**變現**：AdMob 橫幅 + 獎勵廣告（看一支廣告解鎖 Pro 6 小時，可堆疊至 24h；舊的插頁式 mocking 觸發已移除）+ Google Play Billing（月繳訂閱 `mockgps_pro_monthly`：解鎖 Pro 並完全移除橫幅）。
```

Update the developer Pro flag note to add ad-unlock awareness:

```
**開發者 Pro 旗標**：`DEV_FORCE_PRO` 為 `BuildConfig` 旗標（debug=true、release=false），影響 `isProActive` 與 `isAdFreeActive` 兩個 StateFlow，但不會寫入 `AdUnlockStore` 的 expiry。實機驗證 ad-unlock 流程時，需設 `DEV_FORCE_PRO=false`。
```

- [ ] **Step 2: Add new critical constraint section**

Under "重要限制 / Critical Constraints", add a new subsection before "測試規則":

```markdown
### Pro 解鎖兩個維度（必須分辨）
- `isProActive`：訂閱 OR ad-unlock 未過期 → 控制 Pro 功能（路線、搖桿、匯入匯出）
- `isAdFreeActive`：僅訂閱 → 控制橫幅廣告（`MapScreen.kt` line 408）
- **新增需要 banner gate 的地方一律用 `isAdFreeActive`，新增需要 feature gate 的地方一律用 `isProActive`**
- 看廣告解鎖每次 +6h，累積上限 24h（`ProRepositoryImpl.grantAdUnlockHours` 已 cap）
- `RewardedAdManager` 是 Hilt `@Singleton`，由 `MapViewModel.init` 與 `SettingsViewModel.init` 各預載一次
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add rewarded-ad unlock rules and isAdFreeActive gating to CLAUDE.md"
```

---

## Task 11: Final verification (no code changes)

- [ ] **Step 1: Full test suite**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: all tests green, no Kotlin compile warnings new to this PR.

- [ ] **Step 2: Lint**

```bash
./gradlew :app:lintDebug
```
Expected: BUILD SUCCESSFUL. Investigate any new warnings.

- [ ] **Step 3: Debug + release APK assembly**

```bash
./gradlew :app:assembleDebug :app:assembleRelease
```
Expected: both succeed. Release APK uses real ad unit IDs.

- [ ] **Step 4: Manual smoke test checklist on emulator/device (debug)**

Run through this list on a debug install:

- [ ] Free user: Settings shows two cards (看廣告 / 訂閱)
- [ ] Free user: banner shows on MapScreen
- [ ] Free user: tapping any Pro feature shows `ProUpgradeDialog` with both buttons
- [ ] Tap 看廣告 → Google test rewarded plays → reward fires → +6h
- [ ] Settings switches to "Pro 解鎖中, 剩 5h 59m" with progress bar
- [ ] Pro features now usable (open joystick, build a route)
- [ ] Banner still visible on MapScreen during ad-unlock
- [ ] Start mocking does NOT show an interstitial (gone forever)
- [ ] Watch ad again → remaining ≈ 11h 59m
- [ ] Push remaining to ≥ 18h → "再看一支" button disabled, cap note visible
- [ ] Set `DEV_FORCE_PRO=true` in `BuildConfig`, rebuild → Settings shows "Pro 訂閱中", banner gone

- [ ] **Step 5: No commit (this task only verifies)**

If anything fails, return to the relevant task to fix.

---

## Self-Review Checks Performed

- ✅ Spec §3 (state model) → Tasks 3, 6, 7, 8
- ✅ Spec §4 (24h cap) → Task 3 step 4 + 9 test cases
- ✅ Spec §5 (DataStore) → Task 1
- ✅ Spec §6 (AdMob Rewarded + BuildConfig) → Tasks 2, 4
- ✅ Spec §7 (Settings UI 3 states) → Task 9
- ✅ Spec §8 (ProUpgradeDialog) → Task 7
- ✅ Spec §9 (banner gate + interstitial removal) → Tasks 5, 6
- ✅ Spec §10 (edge cases) → covered across Tasks 3 (cap, expired-baseline), 7 (no-fill), 8 (Subscribed override)
- ✅ Spec §11 (test plan) → Tasks 1, 2, 3, 8
- ✅ Spec §12 (file list) → matches plan File Structure
- ✅ Spec §14 (success criteria) → Task 11 smoke checklist
- ✅ Type consistency: `grantAdUnlockHours(hours: Long)` used identically in Tasks 3, 7, 8
- ✅ Type consistency: `isAdFreeActive: StateFlow<Boolean>` consistent in Tasks 3, 6, 7, 8
- ✅ Type consistency: `MapUiState.isAdFreeActive: Boolean` matches `proRepository.isAdFreeActive.value` direction (collected, not exposed)
- ✅ No placeholders: every code step contains complete code; no "TODO" / "fill in"
