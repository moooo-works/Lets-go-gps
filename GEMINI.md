# GEMINI.md - MockGPS 專案開發指引

此檔案為 Gemini CLI 提供 MockGPS 專案的上下文、架構規範與開發準則。本專案所有對話與文件一律使用 **繁體中文**。

## 專案概覽
MockGPS 是一款專業級的 Android 應用程式，旨在協助開發者與 QA 工程師模擬 GPS 位置與路線。
- **核心功能**：單點定位模擬、多點路線模擬、速度調整、位置收藏、歷史記錄。
- **技術棧**：Kotlin, Jetpack Compose, Hilt (DI), Room (DB), Google Maps Compose SDK, Coroutines/Flow.

## 核心架構
專案遵循 **Clean Architecture + MVVM**，分為三層：

1. **UI 層 (`ui/`)**：
   - 使用 Jetpack Compose 構建畫面。
   - 每個功能模組包含 `Screen.kt` 與 `ViewModel.kt`。
   - **導航規則**：ViewModel 必須在 `NavHost` 的 `composable` lambda 內建立，不得在 Composable 參數中直接呼叫 `hiltViewModel()`。
   - **狀態傳遞**：跨畫面結果使用 `SavedStateHandle` 傳遞。

2. **Domain 層 (`domain/`)**：
   - 定義 `LocationMockEngine` 介面。
   - `RouteSimulator`：單例物件，負責處理路線插值與移動邏輯。
   - 定義 Repository 介面。

3. **Data 層 (`data/`)**：
   - `AndroidLocationMockEngine`：透過 `LocationManager` 實作 mock 注入。
   - Room 資料庫實作（`AppDatabase`, `LocationDao`, `RouteDao`）。
   - Repository 具體實作。

## 開發規範與指令

### 建置與測試
- **編譯 Debug APK**：`./gradlew assembleDebug`
- **安裝至裝置**：`./gradlew installDebug`
- **執行單元測試**：`./gradlew test`
- **執行特定測試**：`./gradlew test --tests <完整類別路徑>`
- **Lint 檢查**：`./gradlew lintDebug`

### 重要開發守則
- **測試先行與全面覆蓋**：
  - **新功能開發**必須包含對應的自動化單元測試（Unit Tests）。
  - **Bug 修復**前須先撰寫能重現問題的測試案例。
  - 每次修改核心邏輯（如 `RouteSimulator` 或 ViewModel）後，必須更新並執行相關測試，確保無回歸錯誤（Regression）。
  - 測試案例須涵蓋**邊界情況**（如極端座標、網路中斷、權限拒絕等）。
- **權限處理**：必須嚴格區分 `Allowed`（允許）、`NotAllowed`（未在開發者選項選取此 App）、`CheckFailed`（檢查失敗）三種狀態。
- **地圖 UX**：準星（Crosshair）必須在地圖容器中心，平移地圖時不得出現意外的 Marker。
- **背景服務**：`MockLocationService` 是核心背景服務，管理 mock provider 的生命週期。修改時需確保 Notification 狀態與實際狀態同步。
- **ViewModel 建立樣式**：
  ```kotlin
  composable("route_name") { backStackEntry ->
      val vm = hiltViewModel<MyViewModel>(backStackEntry)
      MyScreen(vm)
  }
  ```

## 關鍵檔案清單
| 檔案 | 用途 |
| :--- | :--- |
| `MockLocationService.kt` | 背景服務，驅動 Mock 邏輯 |
| `LocationMockEngine.kt` | 抽象定位模擬行為的介面 |
| `RouteSimulator.kt` | 路線模擬的插值與狀態管理核心 |
| `MainActivity.kt` | 應用程式進入點與導航中心 |
| `CLAUDE.md` | 詳細的開發規範與待辦事項 |
| `AGENTS.md` | 多 Agent 協作規範 |

## 注意事項
- **API Key**：Google Maps API Key 應存放於 `local.properties` 的 `MAPS_API_KEY`。
- **Mock 位置權限**：執行前需在裝置的「開發者選項」中將「MockGPS」選為模擬位置應用程式。
- **設計稿**：專案根目錄的 `pencil-new.pen` 包含最新的 UI 設計規格。
