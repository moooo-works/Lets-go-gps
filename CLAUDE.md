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

MockGPS 是一款供開發者與 QA 工程師模擬 GPS 位置與路線的 Android 應用程式，透過 Foreground Service 將假位置注入 Android 系統。

MockGPS is an Android app that lets developers/QA engineers simulate GPS locations and routes. It runs a foreground service to inject mock locations into the Android system.

Google Maps API 金鑰須設定於 `local.properties`：
```
MAPS_API_KEY=your_api_key_here
```

---

## 建置與測試指令 / Build & Test Commands

```bash
./gradlew assembleDebug        # 建置 debug APK / Build debug APK
./gradlew installDebug         # 建置並安裝至裝置 / Build and install on connected device
./gradlew test                 # 執行所有單元測試 / Run all unit tests
./gradlew lintDebug            # 執行 lint 檢查 / Run lint checks

# 執行單一測試類別 / Run a single test class
./gradlew test --tests com.example.mockgps.ui.map.MapViewModelTest
```

PR 合併前，`test`、`lintDebug`、`assembleDebug` 三項皆須通過。
All three commands (`test`, `lintDebug`, `assembleDebug`) must pass before any PR can be merged.

---

## 架構 / Architecture

**Clean Architecture + MVVM**，分為三層：

- **UI 層** (`ui/`)：Jetpack Compose 畫面 + ViewModels（Map、SavedLocations、Routes、Settings）
- **Domain 層** (`domain/`)：`LocationMockEngine` 介面、`RouteSimulator` singleton、Repository 介面
- **Data 層** (`data/`)：Room 資料庫、`AndroidLocationMockEngine`、Repository 實作

**DI**：Hilt（`MainApplication` 標註 `@HiltAndroidApp`），模組位於 `di/`。

**導航**：`MainActivity` 中單一 `NavHost`，四個路由：`map`、`saved_locations`、`routes`、`settings`。

**背景服務**：`MockLocationService` 為 Foreground Service，管理 Android mock location provider 的生命週期，並驅動 `RouteSimulator` 更新。

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
- 儲存位置的 marker 只顯示名稱（不顯示經緯度 snippet）
- 從列表選取儲存位置後，地圖須 animate 置中並更新中心座標顯示

---

## 關鍵檔案 / Key Files

| 檔案 / File | 用途 / Purpose |
|------------|---------------|
| `app/src/main/java/com/example/mockgps/service/MockLocationService.kt` | Foreground service；負責 mock provider 生命週期 |
| `app/src/main/java/com/example/mockgps/domain/LocationMockEngine.kt` | 抽象 Android location mocking 的介面 |
| `app/src/main/java/com/example/mockgps/domain/RouteSimulator.kt` | Singleton；沿路線插值計算位置 |
| `app/src/main/java/com/example/mockgps/MainActivity.kt` | NavHost 與應用程式進入點 |
| `gradle/libs.versions.toml` | 集中管理依賴版本 |
| `AGENTS.md` | 多 Agent 協作規範（分支策略、合併門檻） |
| `pencil-new.pen` | Pencil UI 設計稿（4 個畫面：Map Dark/Light、Search Dialog Dark/Light） |

---

## UI 待辦事項 / UI TODO

### 已完成 / Done
- [x] `feat/theme-system`：Color.kt、Type.kt、Theme.kt、MainActivity 套用 MockGpsTheme
- [x] Pencil 設計稿：Map Screen Dark/Light（含搜尋 FAB）
- [x] Pencil 設計稿：Search Dialog Dark/Light（含 Normal / Error 兩種狀態）

### 待實作（建議套用順序）/ Pending (suggested order)
1. **BottomNav 樣式**：將 Jules 實作的底部導覽列套用設計色彩（選中 Green400，未選中 TextSecondaryDark/Light）
2. **MapScreen UI**：TopBar 座標顯示、準星（crosshair）、綠色圓形搜尋 FAB、BottomPanel 重新排版
3. **SavedLocationsScreen UI**：列表項目、新增按鈕、Import/Export 功能
4. **RoutesScreen UI**：路線管理列表
5. **SettingsScreen UI**：設定項目
6. **字體升級**：將 Sora（標題）與 Inter（內文）TTF 加入 `res/font/`，更新 `Type.kt`

### 設計備註 / Design Notes
- Dark 模式背景在 Pencil 截圖工具中不顯色（#0c0c0c 等深色 fill 截圖呈白色），但資料結構正確
- Search Dialog 設計稿包含 Normal 與 Error 兩種狀態，對應程式中 `LocationQueryParser.parse()` 的成功／失敗路徑
