# Rewarded Ad → 6h Pro 解鎖設計

**日期**：2026-05-19
**狀態**：待 review

---

## 1. 背景

目前 MockGPS 變現只有兩條路：

- **Banner**：MapScreen 底部，免費用戶持續露出
- **Interstitial**：免費用戶每次「開始 mocking」前播放
- **訂閱**：$3.99 / 月，全部 Pro 功能 + 移除廣告

工具類 app 的訂閱轉換率偏低（1–3%），但「看一支廣告換 6h 功能」的轉換率可達 30–60%。MockGPS 的使用情境是突發性（QA 一次測試 session），6h 解鎖剛好涵蓋一個工作 session，產品適配度很高。

同時，「開始 mocking 前的 interstitial」對重度使用者是反覆出現的摩擦，移除後使用者體驗顯著提升、且整體廣告策略更聚焦。

## 2. 目標

1. 新增「看一支獎勵廣告 → 解鎖 Pro 6 小時」入口（Settings 頁 + ProUpgradeDialog）
2. 移除「開始 mocking 前的 interstitial」整套機制
3. 區分「Pro 功能解鎖」與「移除廣告」兩個獨立開關：
   - 看廣告解鎖 = 功能可用，但 banner 仍露（升級壓力）
   - 訂閱 = 功能可用 + 完全無 banner
4. 累積上限 24h（看 1 支 +6h，最多疊到 24h）
5. 已訂閱用戶完全看不到「看廣告」按鈕

## 3. 狀態模型

`ProRepository` 介面擴充為：

```kotlin
interface ProRepository {
    val isProActive: StateFlow<Boolean>        // subscribed || adUnlockExpiry > now
    val isAdFreeActive: StateFlow<Boolean>     // subscribed only
    val adUnlockExpiryMillis: StateFlow<Long>  // 0 = 未解鎖；> 0 = 到期 epoch millis

    suspend fun refreshProStatus()
    suspend fun grantAdUnlockHours(hours: Long)
    fun launchBillingFlow(activity: Activity)
}
```

`ProRepositoryImpl` 內部：

- 注入 `BillingManager`、`AdUnlockStore`（DataStore wrapper）
- `isProActive` = `combine(billingManager.isProActive, adUnlockExpiryMillis) { sub, expiry → sub || expiry > System.currentTimeMillis() }`
- `isAdFreeActive` = `billingManager.isProActive`（pass-through）
- 配合一個內部 ticker（每 60s 重算）以便 `isProActive` 在到期那刻能正確翻轉

替代方案考慮：
- 拆出獨立 `AdUnlockRepository` → 過度設計，所有消費端都要 combine 兩個來源
- 塞進 `BillingManager` → 混淆訂閱與廣告兩件事，未來換 billing provider 會很痛

選擇現有方案（單一 `ProRepository` 兩個 derived flow）保持單一真實來源。

## 4. 24h 上限規則

`grantAdUnlockHours(h)` 邏輯：

```kotlin
val now = System.currentTimeMillis()
val currentExpiry = adUnlockExpiryMillis.value
val newExpiry = maxOf(currentExpiry, now) + h * 3600_000L
val cap = now + 24 * 3600_000L
val capped = minOf(newExpiry, cap)
adUnlockStore.setExpiry(capped)
```

UI 額外保護：剩餘時間 ≥ 18h 時，看廣告按鈕 disabled（避免白看廣告卻只能加少少時間）。

## 5. 持久化

新建 `data/billing/AdUnlockStore.kt`：

```kotlin
@Singleton
class AdUnlockStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.adUnlockDataStore
    private val EXPIRY_KEY = longPreferencesKey("pro_ad_unlock_expiry_millis")

    val expiryFlow: Flow<Long> = dataStore.data.map { it[EXPIRY_KEY] ?: 0L }
    suspend fun setExpiry(millis: Long) { dataStore.edit { it[EXPIRY_KEY] = millis } }
}
```

使用 epoch millis（`System.currentTimeMillis()`）。`SystemClock.elapsedRealtime` 重開機歸零、不適合；接受使用者改系統時間能規避的低風險（rewarded ad 價值低，反作弊性價比差）。

## 6. AdMob Rewarded Ad

新建 `data/billing/RewardedAdManager.kt`（與 `InterstitialAdManager` 同層，但 Interstitial 會被刪除）。為了在 Settings 與 ProUpgradeDialog 之間共用同一個 preload 實例，採 **Hilt `@Singleton`** 而非 InterstitialAdManager 原本的 `remember { ... }` 模式：

```kotlin
@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun preload() {
        if (isLoading || rewardedAd != null) return
        isLoading = true
        RewardedAd.load(
            context,
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad; isLoading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null; isLoading = false }
            }
        )
    }

    fun showAd(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) { onUnavailable(); preload(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewardedAd = null; preload() }
        }
        ad.show(activity) { _ -> onReward() }
    }
}
```

BuildConfig 增改：

```kotlin
// app/build.gradle.kts
debug {
    // 移除：buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", ...)
    buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
}
release {
    // 移除：buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", ...)
    buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-TODO_REPLACE_ME/REWARDED\"")
}
```

Release ID 先用 placeholder（`ca-app-pub-TODO_REPLACE_ME/REWARDED`），等使用者在 AdMob 後台建立「獎勵廣告」單元後替換。

## 7. Settings 頁 UI

頂部新增「Pro 訂閱」section，依狀態三態切換：

### 狀態 A：完全免費（無訂閱、無 ad-unlock）

```
┌─────────────────────────────────────────┐
│ 🎁 Pro 升級                              │
│                                          │
│ ┌─────────────────┐ ┌─────────────────┐│
│ │ 看廣告解鎖       │ │ 訂閱無廣告版    ││
│ │ 6 小時 Pro       │ │ $3.99 / 月       ││
│ │ [看廣告]         │ │ [訂閱]           ││
│ └─────────────────┘ └─────────────────┘│
└─────────────────────────────────────────┘
```

### 狀態 B：Ad-unlock 中

```
┌─────────────────────────────────────────┐
│ ✨ Pro 解鎖中（剩 4h 32m）              │
│ [████████░░░░] 進度條                    │
│                                          │
│ [再看一支 +6h]   [升級訂閱（永久無廣告）]│
│                                          │
│ ※ 累積上限 24h                           │
└─────────────────────────────────────────┘
```

剩餘 ≥ 18h 時「再看一支」按鈕 disabled，附文字「已接近 24h 上限」。

### 狀態 C：訂閱中

```
┌─────────────────────────────────────────┐
│ ✅ Pro 訂閱中                            │
│ [管理訂閱]                               │
└─────────────────────────────────────────┘
```

「看廣告」按鈕完全不顯示。

### 倒數時間顯示

`SettingsViewModel` 提供一個每 60s tick 的 `remainingMillis` StateFlow（用 `flow { while (true) { emit(...); delay(60_000) } }`）。UI 顯示格式：

- ≥ 60min：`Xh Ym`
- < 60min：`Ym`
- < 1min：`不到 1 分鐘`

## 8. ProUpgradeDialog

Pro 功能被點按時跳出的對話框新增第二顆按鈕：

```
┌────────────────────────────────────────┐
│ 升級 Pro 解鎖此功能                    │
│                                         │
│ ✦ 路線模擬、搖桿、匯入匯出、無限儲存   │
│                                         │
│ [看一支廣告解鎖 6 小時]                 │
│ [訂閱 $3.99 / 月]                       │
│ [取消]                                  │
└────────────────────────────────────────┘
```

`ProUpgradeDialog` 是純 Composable，不直接持有 `RewardedAdManager`。對話框介面新增兩個 lambda：

```kotlin
@Composable
fun ProUpgradeDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,           // 新增
    onSubscribe: () -> Unit,         // 原 onUpgrade 改名
    watchAdEnabled: Boolean = true   // 新增（剩 ≥ 18h 時 false）
)
```

呼叫端（`MapViewModel.requestProUpgrade` / `SettingsViewModel`）注入 `RewardedAdManager` 並把 callback 串接到 `proRepository.grantAdUnlockHours(6)`。

Preload 時機：`SettingsViewModel.init` 與 `MapViewModel.init` 各呼叫一次 `rewardedAdManager.preload()`；看完一支後 `RewardedAdManager` 內部 `onAdDismissedFullScreenContent` 會自動 preload 下一支。

## 9. Banner 與 Interstitial 行為

### Banner（保留，gate 改用 `isAdFreeActive`）

`MapScreen.kt:408`：

```kotlin
// 改前：if (!uiState.isProActive) BannerAdView()
if (!uiState.isAdFreeActive) BannerAdView()
```

訂閱者 banner 消失，其他人（含 ad-unlock）持續顯示。

### Interstitial（完全移除）

刪除：

- `data/billing/InterstitialAdManager.kt` 整個檔案
- `app/build.gradle.kts` 兩處 `INTERSTITIAL_AD_UNIT_ID` buildConfigField
- `MapScreen.kt:87` 的 `interstitialAdManager = remember { InterstitialAdManager(context) }`
- `MapBottomPanel.kt:71` 的 `interstitialAdManager: InterstitialAdManager` 參數
- `MapBottomPanel.kt:135` 的 `interstitialAdManager.showAd(activity) { onStartMocking() }`，改回直接 `onStartMocking()`

`InterstitialAdManager` 沒有其他呼叫點（grep 確認過），可以淨刪。

## 10. 邊界情況

| 情境 | 處理 |
|------|------|
| AdMob no-fill | `onUnavailable` callback 觸發 → 顯示 snackbar「暫時沒有可用廣告，請稍後再試」；同時呼叫 `preload()` 重試 |
| 廣告中途關閉（未看完） | AdMob 不觸發 `onUserEarnedReward`，不發放獎勵 |
| 解鎖期間訂閱 | `isProActive` 由 OR 計算，訂閱優先；ad-unlock expiry 保留但不影響行為（過期自然忘記） |
| 解鎖期間取消訂閱 | 訂閱 false → 退回 ad-unlock 判斷；若還沒過期則 Pro 仍解鎖中 |
| 系統時間被竄改 | 接受風險，不防（rewarded 單次收益低，反作弊性價比差） |
| 剩 23h 又看廣告 | 後端 cap 到 24h；前端按鈕 disabled 防白看 |
| App 在 ad-unlock 期間被殺 | DataStore 持久化，重開後仍記得到期時間 |
| 解鎖剛到期那一刻 | ticker 每 60s 重算 `isProActive`；最壞延遲 60s，可接受 |
| 訂閱與 ad-unlock 同時存在 | 訂閱優先（`isAdFreeActive` 為 true，banner 消失）；ad-unlock 不需手動清除 |

## 11. 測試計畫

### `ProRepositoryImplTest`（新增）

| 案例 | 預期 |
|------|------|
| 無訂閱 + 無 ad-unlock | `isProActive=false`, `isAdFreeActive=false` |
| 無訂閱 + ad-unlock 未到期 | `isProActive=true`, `isAdFreeActive=false` |
| 無訂閱 + ad-unlock 已到期 | `isProActive=false`, `isAdFreeActive=false` |
| 訂閱中 + 無 ad-unlock | `isProActive=true`, `isAdFreeActive=true` |
| 訂閱中 + ad-unlock 未到期 | `isProActive=true`, `isAdFreeActive=true` |
| `grantAdUnlockHours(6)` 從零 | expiry = now + 6h |
| `grantAdUnlockHours(6)` 連呼三次 | expiry = now + 18h |
| `grantAdUnlockHours(6)` 第五次 | capped at now + 24h |
| 已 23h + grant 6h | capped at now + 24h |

實作上 `ProRepositoryImpl` 建構式新增 `clock: () -> Long = { System.currentTimeMillis() }` 參數，測試注入可控時間；ticker 用 `kotlinx.coroutines.delay` 配合 `TestScope` 推進虛擬時鐘。

### `SettingsViewModelTest`（補充）

| 案例 | 預期 |
|------|------|
| 三種 Pro 狀態 → UI section state | 對應 A / B / C |
| 倒數時間格式化 | `5h 30m`、`45m`、`不到 1 分鐘` |
| 看廣告 reward callback | 呼叫 `proRepository.grantAdUnlockHours(6)` |
| 剩餘 ≥ 18h 時看廣告按鈕 | disabled=true |

### `RewardedAdManagerTest`

`RewardedAd` 是 final class 不易 mock，採輕量整合測試：抽出 inner interface `RewardedAdLoader`，注入 fake 模擬載入成功 / 失敗 / 看完獎勵 / 中途關閉，驗證 callback 觸發路徑。Production 用真的 AdMob SDK。

### 既有測試影響

- `MapViewModelTest`：增加 `proRepository` mock 的 `isAdFreeActive` flow（預設 false）
- `MapBottomPanel`（無單元測試，UI 層）：手動 smoke test

## 12. 涉及檔案

### 新建

| 路徑 | 用途 |
|------|------|
| `app/src/main/java/com/moooo_works/letsgogps/data/billing/RewardedAdManager.kt` | 獎勵廣告管理器（Hilt `@Singleton`） |
| `app/src/main/java/com/moooo_works/letsgogps/data/billing/AdUnlockStore.kt` | DataStore wrapper for expiry |
| `app/src/test/java/com/moooo_works/letsgogps/data/repository/ProRepositoryImplTest.kt` | Pro state combine 測試 |
| `app/src/test/java/com/moooo_works/letsgogps/data/billing/RewardedAdManagerTest.kt` | Rewarded ad callback 測試 |

### 修改

| 路徑 | 修改 |
|------|------|
| `domain/repository/ProRepository.kt` | +3 介面成員 |
| `data/repository/ProRepositoryImpl.kt` | combine 邏輯 + ticker |
| `di/RepositoryModule.kt` | 注入 `AdUnlockStore` |
| `ui/settings/SettingsScreen.kt` | 頂部新增 Pro 區塊（三狀態） |
| `ui/settings/SettingsViewModel.kt` | `proSection` state + `remainingMillis` ticker + `watchRewardedAd` |
| `ui/pro/ProUpgradeDialog.kt` | 加「看廣告解鎖 6h」按鈕 |
| `ui/map/MapScreen.kt` | line 408 banner gate 改 `isAdFreeActive`；刪除 line 87 interstitial |
| `ui/map/MapBottomPanel.kt` | 刪除 `interstitialAdManager` 參數與 line 135 呼叫 |
| `ui/map/MapViewModel.kt` | `MapUiState.isAdFreeActive` 對應 |
| `ui/map/MapState.kt` | 新增 `isAdFreeActive` 欄位 |
| `app/build.gradle.kts` | 移除 `INTERSTITIAL_AD_UNIT_ID`，新增 `REWARDED_AD_UNIT_ID` |
| `app/src/test/.../SettingsViewModelTest.kt` | 補三狀態與 reward callback 測試 |
| `app/src/test/.../MapViewModelTest.kt` | 補 `isAdFreeActive` 預設值 |
| `CLAUDE.md` | 新增 ad-unlock 邏輯說明、變現策略段落 |

### 刪除

| 路徑 | 原因 |
|------|------|
| `app/src/main/java/com/moooo_works/letsgogps/ui/ads/InterstitialAdManager.kt` | 整套 interstitial 機制移除 |

合計：4 新建 + 13 修改 + 1 刪除 = 18 檔。

## 13. 不在範圍

- 廣告獎勵時數不做成可設定 / 遠端 config（hardcode 6h）
- 不做一次性買斷 IAP（之後可加，獨立 spec）
- 不做反作弊（系統時間竄改、root 注入廣告 reward 等）
- 不做 A/B test 框架
- 不調整訂閱 SKU 或定價
- 不更動既有 banner 位置或樣式

## 14. 成功標準

- `./gradlew test lintDebug assembleDebug` 全綠
- Free → Settings 點「看廣告」→ 看完 30s rewarded → Pro 解鎖 6h，Pro 功能（路線、搖桿、匯入匯出）可用
- Ad-unlock 期間 banner 仍露，「開始 mocking」不出現 interstitial
- 訂閱者完全無 banner、無看廣告按鈕
- 累積看 5 支廣告 expiry 不超過 now + 24h
- 過期後 Pro 功能正確再次被鎖
