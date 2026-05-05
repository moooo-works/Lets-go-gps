# 設計文件：資料夾分類 ＋ GPX 匯入

**日期**：2026-05-05  
**狀態**：已核准  

---

## 背景

兩個功能來自使用者回饋（Play Store 評論，FDM CHEN，2026-05-05）及外部工具整合需求（暖風 GPX 生成器）。

---

## 一、資料夾分類功能

### 設計決策

| 決策 | 選擇 |
|------|------|
| 資料夾與最愛的關係 | **並存**——最愛 flag 保留，資料夾是獨立分類層 |
| 一個位置能屬於幾個資料夾 | **只能一個**（nullable FK） |
| 資料夾 UI 入口 | **單列 Chip**（全部、最愛、各資料夾橫向排列，可滑動） |
| 指定位置到資料夾 | **批次選取模式**（長按進入多選，底部 action bar 操作） |
| 資料夾管理入口 | **TopBar「📁 管理」按鈕**，進入獨立管理頁 |

### 資料模型（schema v5）

新增資料表：

```sql
CREATE TABLE location_folders (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT    NOT NULL,
    createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);
```

`saved_locations` 新增欄位：

```sql
ALTER TABLE saved_locations
ADD COLUMN folderId INTEGER NULL REFERENCES location_folders(id) ON DELETE SET NULL;
```

Migration 4→5：執行上述兩條 SQL。

刪除資料夾時，該資料夾內的位置 `folderId` 自動設為 `NULL`（歸入「未分類」），位置本身不受影響。

### UI 細節

**儲存位置頁**

- TopBar 右側新增「📁 管理」圖示按鈕
- 搜尋列下方 `LazyRow`（可橫向滑動）顯示篩選 Chip：
  - `全部`（預設選中）
  - `❤️ 最愛`（對應原有 isFavorite 篩選）
  - 每個資料夾各一個 chip（依 createdAt 排序）
  - 一次只能選一個 chip
- 現有「最愛/歷史」toggle 移除，由 chip 取代

**批次選取模式**

- 長按任一位置項目 → 進入多選模式
- 列表項目左側出現 checkbox
- 底部 action bar：`移到資料夾 ｜ 刪除 ｜ 取消`
- 點「移到資料夾」→ 彈出 Dialog，列出所有資料夾＋「未分類」選項，單選後確認

**資料夾管理頁**

- 獨立 Composable（`FolderManagementScreen`）
- 列出所有資料夾，每項顯示名稱＋位置數量
- 每項右側：重新命名（✏️）、刪除（🗑）圖示按鈕
- 刪除時顯示確認 Dialog，說明「資料夾內的位置將移至未分類」
- 底部「＋ 新增資料夾」按鈕，點擊彈出輸入名稱 Dialog

### 新增 / 修改的檔案

| 檔案 | 變更 |
|------|------|
| `data/model/LocationFolder.kt` | 新增 Entity |
| `data/local/AppDatabase.kt` | 版本升至 5，加 MIGRATION_4_5，entities 加 LocationFolder |
| `data/local/LocationFolderDao.kt` | 新增 DAO（CRUD + 查詢資料夾含位置數） |
| `data/local/LocationDao.kt` | 查詢加 folderId 篩選參數 |
| `data/model/SavedLocation.kt` | 加 `folderId: Int?` 欄位 |
| `domain/repository/LocationRepository.kt` | 加資料夾相關方法 |
| `data/repository/LocationRepositoryImpl.kt` | 實作資料夾方法 |
| `ui/savedlocations/SavedLocationsViewModel.kt` | 加資料夾篩選、批次選取、管理邏輯 |
| `ui/savedlocations/SavedLocationsScreen.kt` | Chip 列、批次選取模式、管理按鈕 |
| `ui/savedlocations/FolderManagementScreen.kt` | 新增資料夾管理頁 |
| `MainActivity.kt` | 加資料夾管理頁路由（或 Dialog 方式） |

---

## 二、GPX 匯入功能

### 設計決策

| 決策 | 選擇 |
|------|------|
| 觸發入口 | 設定頁「資料管理」區塊，現有匯入按鈕擴充支援 GPX |
| Waypoints 處理 | 存成 `SavedLocation` |
| Tracks / Routes 處理 | 存成 `Route` ＋ `RoutePoint` |
| 解析器 | Android 內建 `XmlPullParser`，不需額外依賴 |

### 解析邏輯

檔案選擇：`ActivityResultContracts.OpenDocument`，MIME type `*/*`（GPX 無標準 MIME，需接受副檔名過濾）。

| GPX 節點 | 對應資料 | 名稱來源 |
|---------|---------|---------|
| `<wpt lat="" lon="">` | `SavedLocation` | `<name>` 子節點，無則用座標字串 |
| `<trk>` 或 `<rte>` | `Route` ＋ 點序列 | `<name>` 子節點，無則用檔名 |
| `<trkpt>` / `<rtept>` | `RoutePoint`（屬於上層 Route） | — |

解析流程：
1. 以 `XmlPullParser` 線性掃描 GPX 檔案
2. 遇到 `<wpt>` → 累積為 `SavedLocation` 列表
3. 遇到 `<trk>` / `<rte>` → 開始累積 RoutePoint，結束標籤時存為一條 Route
4. 完成後批次 insert（同一 transaction）
5. 回傳 `ImportResult(locationsAdded: Int, routesAdded: Int)`

### 設定頁 UI

「資料管理」區塊的「匯入」改為 DropdownMenu 或直接開啟格式選擇 Dialog：

```
匯入 ▼
  ├── 從備份檔匯入（.json）
  └── 從 GPX 匯入（.gpx）
```

匯入完成後顯示 Snackbar：
- 成功：`匯入完成：3 個位置、1 條路線`
- 部分成功：`匯入完成：2 個位置（1 筆格式錯誤已略過）`
- 失敗：`GPX 格式錯誤，無法解析`

### 新增 / 修改的檔案

| 檔案 | 變更 |
|------|------|
| `data/gpx/GpxParser.kt` | 新增 GPX 解析器 |
| `domain/repository/LocationRepository.kt` | 加 `importFromGpx(uri)` 或拆成 locations/routes |
| `data/repository/LocationRepositoryImpl.kt` | 實作 GPX 匯入邏輯 |
| `ui/settings/SettingsScreen.kt` | 匯入按鈕加 GPX 選項 |
| `ui/settings/SettingsViewModel.kt` | 加 GPX 匯入 handler |

---

## 實作順序建議

1. **GPX 匯入**（獨立、風險低、不動 schema）
2. **資料夾 schema migration**（v5）
3. **資料夾 CRUD 後端**（DAO、Repository）
4. **資料夾 UI**（Chip 列 → 批次選取 → 管理頁）
