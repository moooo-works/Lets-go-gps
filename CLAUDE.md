# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
本檔案提供 Claude Code (claude.ai/code) 在此專案中工作時的指引。

## 語言 / Language

**文件與對話皆以繁體中文回覆。**

---

## 工作守則 / Working Rules

1. **撰寫任何程式碼前，先描述方法並等待批准。**
2. **需求模糊時，先提出澄清問題，再動手寫程式碼。**
3. **完成程式碼後，列出邊界情況並建議對應的測試案例。**
4. **若任務需要修改超過 3 個檔案，先停下來將其拆分成更小的子任務。**
5. **出現 bug 時，先撰寫能重現該 bug 的測試，再修復直到測試通過。**
6. **每次被糾正時，反思犯錯原因，並制定避免再犯的計畫。**
7. **所有文件與回覆一律以繁體中文撰寫。**

---

## 專案概覽 / Project Overview

MockGPS 是一款供開發者與 QA 工程師模擬 GPS 位置與路線的 Android 應用程式，透過 Foreground Service 將假位置注入 Android 系統。支援訂閱制 Pro 方案（路線模擬、搖桿控制、匯入／匯出、無限儲存位置、移除廣告）。

Google Maps API 金鑰須設定於 `local.properties`：
```
MAPS_API_KEY=your_api_key_here
```

---

## 建置與測試指令 / Build & Test Commands

```bash
./gradlew assembleDebug        # 建置 debug APK
./gradlew installDebug         # 建置並安裝至裝置
./gradlew test                 # 執行所有單元測試
./gradlew lintDebug            # 執行 lint 檢查

# 執行單一測試類別
./gradlew test --tests com.moooo_works.letsgogps.ui.map.MapViewModelTest
```

PR 合併前，`test`、`lintDebug`、`assembleDebug` 三項皆須通過。

---

## 架構 / Architecture

**Clean Architecture + MVVM**，分為三層：

- **UI 層** (`ui/`)：Jetpack Compose 畫面 + ViewModels（Map、SavedLocations、Routes、Settings）
- **Domain 層** (`domain/`)：`LocationMockEngine` 介面、`RouteSimulator` singleton、Repository 介面（含 `ProRepository`）
- **Data 層** (`data/`)：Room 資料庫（v3）、`AndroidLocationMockEngine`、`BillingManager`、Repository 實作

**DI**：Hilt（`MainApplication` 標註 `@HiltAndroidApp`），模組位於 `di/`。

**導航**：`MainActivity` 中單一 `NavHost`，四個路由：`map`、`saved_locations`、`routes`、`settings`。

**背景服務**：`MockLocationService` 為 Foreground Service，管理 Android mock location provider 的生命週期，並驅動 `RouteSimulator` 更新。

**變現**：AdMob（橫幅 + 插頁式廣告）+ Google Play Billing（月繳訂閱，ID：`mockgps_pro_monthly`）。

---

## 依賴版本 / Key Dependency Versions

| 項目 | 版本 |
|------|------|
| Kotlin | 1.9.22 |
| KSP | 1.9.22-1.0.17 |
| compose-bom | 2024.09.00（Material3 1.3.0） |
| Compose Compiler | 1.5.8 |
| AGP | 8.3.2 |
| Hilt | 2.48 |
| Room | 2.6.0（schema v3） |

---

## 重要限制 / Critical Constraints

### Navigation / ViewModel 建立規則
- **禁止**在 Screen composable 的預設參數中呼叫 `hiltViewModel()` / `viewModel()`
- **必須**在 `composable { backStackEntry -> }` lambda 內建立 ViewModel：
  ```kotlin
  composable("map") { backStackEntry ->
      val vm = hiltViewModel<MapViewModel>(backStackEntry)
      MapScreen(vm)
  }
  ```
- 跨畫面傳遞結果使用 `SavedStateHandle.getStateFlow()`；消費後須清除 key

### Mock Location 權限——三種狀態（必須分辨）
- `Allowed`：正常操作
- `NotAllowed`：顯示「請到 Developer Options 選擇 mock app」按鈕（只在使用者點擊後才導向，冷啟動不自動跳轉）
- `CheckFailed`：顯示「Permission check failed」訊息；**不得**顯示設定按鈕

Engine 錯誤（setup／setLocation／teardown）須以 engine error 呈現，不得誤報為權限問題。

### 地圖 UX（不得回歸）
- 平移地圖時不得出現非預期的藍色 pin／marker
- 準星以地圖可視容器置中，非整個螢幕置中
- 儲存位置 marker：最愛為紅色、一般為橘色；點擊顯示名稱＋說明泡泡
- 從列表選取儲存位置後，地圖須 animate 置中並更新中心座標顯示

### Edge-to-Edge UI 規則
- `MainActivity` 使用 `enableEdgeToEdge()`，外層 Scaffold 設 `contentWindowInsets = WindowInsets(0)`
- **MapScreen 內層 Scaffold 同樣設 `contentWindowInsets = WindowInsets(0)`**，避免雙重 status bar padding
- 地圖頁浮動元件（搜尋列、FAB、操作卡片）須用 `Box` 包裹後在 `Box` 上套用 `statusBarsPadding()`，**不可直接套在 Surface／FAB 上**

### Dialog / DropdownMenu 顏色規則
- 所有 `AlertDialog` 必須加 `containerColor = MaterialTheme.colorScheme.surface` + `tonalElevation = 0.dp`
- `DropdownMenu` 同樣加 `containerColor = MaterialTheme.colorScheme.surface` + `tonalElevation = 0.dp`（M3 1.3.0+ 支援）
- `Card` 若不需陰影，設 `CardDefaults.cardElevation(defaultElevation = 0.dp)`

### 測試規則
- 新增 Repository 介面方法時，同步更新所有測試中的 Fake 實作類別
- ViewModel 新增 constructor 參數（如 `ProRepository`）時，同步更新所有相關測試的建構呼叫
- `SettingsViewModelTest` 中需 mock `proRepository.isProActive` 回傳 `MutableStateFlow(true)`，確保 export 功能不被 Pro 檢查擋住

---

## 關鍵檔案 / Key Files

| 檔案 | 用途 |
|------|------|
| `app/src/main/java/com/moooo_works/letsgogps/service/MockLocationService.kt` | Foreground service；負責 mock provider 生命週期 |
| `app/src/main/java/com/moooo_works/letsgogps/domain/LocationMockEngine.kt` | 抽象 Android location mocking 的介面 |
| `app/src/main/java/com/moooo_works/letsgogps/domain/RouteSimulator.kt` | Singleton；沿路線插值計算位置 |
| `app/src/main/java/com/moooo_works/letsgogps/MainActivity.kt` | NavHost 與應用程式進入點 |
| `app/src/main/java/com/moooo_works/letsgogps/data/billing/BillingManager.kt` | Google Play Billing 訂閱管理 |
| `app/src/main/java/com/moooo_works/letsgogps/domain/repository/ProRepository.kt` | Pro 訂閱狀態介面 |
| `app/src/main/java/com/moooo_works/letsgogps/ui/pro/ProUpgradeDialog.kt` | 付費升級提示 Dialog |
| `gradle/libs.versions.toml` | 集中管理依賴版本 |
| `AGENTS.md` | 多 Agent 協作規範（分支策略、合併門檻） |

---

## 功能完成狀態 / Feature Status

### 已完成
- [x] 主題系統（深色／淺色／跟隨系統）
- [x] Edge-to-edge 沉浸式顯示
- [x] 地圖頁：搜尋、準星、座標顯示、地圖樣式切換、定位按鈕
- [x] 地圖頁：儲存位置 marker（最愛紅色／一般橘色）、泡泡視窗、點擊後操作列（刪除／最愛／編輯）
- [x] 地圖頁：搖桿控制（Pro 限定）、路線模擬（Pro 限定）
- [x] 儲存位置頁：列表、搜尋、篩選、排序、重新命名、刪除、最愛、說明欄位
- [x] 儲存位置頁：⋮ 選單「清除非最愛位置」
- [x] 設定頁：主題切換、高度設定、座標抖動、匯入／匯出（Pro）、開發者選項、隱私政策
- [x] 設定頁：「清除非最愛位置」（資料管理區塊）
- [x] 變現：AdMob 橫幅 + 插頁式廣告、Google Play Billing 月繳訂閱、ProUpgradeDialog
- [x] Room 資料庫 schema v3（新增 `description` 欄位）

### 待完成
- [ ] 上架前：替換測試廣告 ID 為正式 ID，建立 Play Console 訂閱方案
- [ ] 字體升級：Sora（標題）、Inter（內文）
