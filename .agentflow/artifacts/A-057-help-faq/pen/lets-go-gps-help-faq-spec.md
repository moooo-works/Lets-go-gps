# Lets-go-gps · 使用說明與 FAQ — 實作規格（Compose）

設計來源：`untitled.pen` → 畫板 `ZPCQt`「Lets-go-gps 使用說明與 FAQ 提案」
PNG：`exports/lets-go-gps-help-faq-four-states.png`（frame `q19J6`，2x，3522×3000 px）

範圍：只重新設計既有 Help 功能的 UI（目錄、搜尋、分類篩選、查無結果、文章）。不新增付款、客服、推薦或任何新操作。全 App 主題不變；以下 token 只用在 Help 畫面。

## Frame IDs

| 狀態 | Light | Dark |
| --- | --- | --- |
| 目錄（全部） | `iJ7rS` | `F61h4H` |
| 文章 · 懸浮工具 | `WGEx8` | `p1Xyly` |
| 搜尋結果「懸浮」 | `WRRi4` | — |
| 查無結果（篩選「常見問題」） | — | `Q8twP` |
| 模組 · 路線模式 A→B→C／循環／往返 | `rOWYj` | `dE20c` |
| 模組 · 搜尋→預覽→移到此處 | `isQCN` | `gKS1B` |
| 360dp 窄螢幕＋英／日／韓長標題 | `Xpdru` | — |
| 字體 130%（文章開頭） | — | `xFrct` |
| 橫向 800×360，內容欄 ≤600dp | `oVVeQ` | — |
| 規格面板 | `d1V5CP` | |

## 色彩 token

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| bg | `#FAFAF8` | `#050A0F` | 畫面底色（沿用既有主題） |
| surface | `#FFFFFF` | `#0A1828` | 分組清單、搜尋框、預期結果區塊 |
| surfaceVariant | `#F3F4F6` | `#0D1520` | 示意圖底、空狀態圖示底 |
| divider | `#ECEEF1` | `#0F2540` | 1dp 外框、內縮分隔線 |
| text | `#1A1A1A` | `#E8F0F8` | 標題、列標題、重點內文 |
| text2 | `#6B7280` | `#E8F0F8` α 0.70 | 次文字、段落、meta（深色約 8:1） |
| iconMuted | `#9CA3AF` | `#4A7A9B` | chevron、手勢條（只用於裝飾，不用在文字） |
| accent | `#FF5722` | `#FF5722` | 圖示、時間軸連線、搜尋框聚焦外框、「移到此處」圓點 |
| accentText | `#C2410C` | `#FF8A65` | 橘色文字，對比 ≥4.5:1（#FF5722 在白底只有 3.2:1，不用在小字） |
| accentSoft | `#FFF0EA` | `#3A2318` | 選取的 chip、序號圓底、分類標籤底（不帶紫色調） |
| onAccent | `#FFFFFF` | `#FFFFFF` | 實心 accent 上的圖示 |

深色 `#4A7A9B` 在 bg 上約 4.3:1，所以文字一律改用 text2（主文字加 alpha）。

## 字級（sp）

| 用途 | 規格 |
| --- | --- |
| Top bar 標題 | 目錄 20 Bold；文章 18 Bold；單行 + ellipsis |
| 文章 H1 | 26 Bold，行高 1.35，不限行數 |
| 摘要 | 16 Regular，行高 1.6，text2 |
| 步驟標題 / 內文 | 16 Bold 行高 1.5 / 15 Regular 行高 1.6 text2 |
| 列標題 | 16 Medium，行高 1.5（24sp），不設 maxLines |
| 區段標題 | 14 Bold，letterSpacing 0.3，text2；右側「N 篇」13 |
| Chip | 14 Medium（選取時 Bold + accentText + 16dp check） |
| Meta / caption | 13 / 12 |
| 序號、Q | 14 Bold / 17 Bold，accentText |

字型：使用系統預設（設計稿用 Noto Sans TC／Roboto 示意），英日韓走系統 fallback。

## 間距與尺寸（dp）

- 水平邊距：目錄 16、文章 20；寬度 < 360dp 時改為 12 / 16。
- 目錄：Top bar 64 → 搜尋框（上 4、下 8 padding，高 52，圓角 14，1dp divider 外框；聚焦時 2dp accent）→ chips 列（高 52，間距 8）→ 列表（上 12、下 28，區段間距 24，區段標題與分組間距 8）。
- 分組容器：surface、圓角 16、1dp divider、clip。列 padding 16/12/16/16，gap 14，**最小高度 56**；leading 欄 28×28；trailing chevron 20；內縮分隔線從 start 58dp 開始。
- Leading 規則：開始使用 = 28dp 序號圓（accentSoft 底）；功能說明 = 22dp 線性圖示（accent，Lucide：picture-in-picture-2、gamepad-2、gauge、route、folder-heart、footprints、crown，可對應到 Material Symbols）；常見問題 = 「Q」字。不再使用每列一張卡片＋淡橘底圖示塊。
- Chip：高 36、膠囊圓角 18、水平 padding 14（選取時 10/14 + check），觸控區 48。
- 文章：標題區（eyebrow 分類標籤 + 「N 個步驟」，gap 10）→ 示意圖區（上 20）→「操作步驟」（上 28，標籤下 14）→ 時間軸 → 預期結果（上 24、下 32）。
- 時間軸：序號圓 28（最小 28，隨字級放大），連線寬 2 divider 色，圓與線間隔 4；步驟間 22。用 `Row(Modifier.height(IntrinsicSize.Min))`，讓連線延伸到文字高度，最後一步不畫線。
- 預期結果：surface、1dp divider、圓角 14、padding 16、circle-check 22 accent ＋「預期結果」14 Bold accentText ＋ 15 內文。
- 示意圖區塊（選用）：surfaceVariant 圓角 16、padding 16；沒有示意圖的文章直接省略，不放空白區塊。
- 觸控：返回、清除、chip、列、空狀態按鈕都 ≥ 48dp（`minimumInteractiveComponentSize`）。

## 行為

- 導覽：文章 → 返回目錄 → 返回原畫面（Settings 或新功能 dialog）。dialog「查看操作教學」直接開懸浮工具文章，返回時到目錄。沒有底部分頁。
- 搜尋：比對標題與內文，即時過濾；有輸入時顯示 48dp 清除鈕。有關鍵字時改為單一結果清單：頂部「N 筆結果 · 搜尋標題與內文」，每列下方顯示「分類 · 標題符合／內文符合」；命中字用 accentText Bold（`AnnotatedString`）；內文命中時多顯示 1 行摘要（14sp text2，前後加 …，maxLines 1）。
- 分類篩選：全部／開始使用／功能說明／常見問題，單選，和搜尋同時生效。
- 查無結果：64dp 圓（surfaceVariant）＋ search-x 28 → 標題 18 Bold（篩選中：「〔分類〕中找不到「關鍵字」」；全部：「找不到「關鍵字」」）→ 建議 15 text2 → 按鈕：分類不是「全部」時顯示「搜尋全部分類」（accentSoft 膠囊 48dp，等同點「全部」chip），另有「清除搜尋」（文字按鈕，等同清除鈕）。分類是「全部」時只顯示「清除搜尋」。

## 長內容 / 適配規則

1. 標題、步驟標題、空狀態標題都不截斷、不設 maxLines；chevron 與 leading 垂直置中。只有 Top bar 標題是單行 ellipsis。
2. Chips 使用 `LazyRow(contentPadding = 16dp)`，可以超出畫面；尾端 24dp 漸層（bg α0 → bg）提示可以橫向滑動。
3. 所有字級都用 sp，容器不固定高度；字體 130% 時列與時間軸自然長高（見 `xFrct`）。
4. 寬度 < 360dp：水平邊距縮小，版面結構不變（見 `Xpdru`，英／日／韓長標題換成 2 行）。
5. 橫向／平板：內容欄 `widthIn(max = 600.dp)` 置中，背景滿版（見 `oVVeQ`）。
6. 長文：步驟超過 4 步仍用同一條時間軸；單步內文超過 4 行時用段落（`\n\n`）拆開，不加展開／收合。文章整頁用一個 `LazyColumn` 或 `verticalScroll`。
7. 文案以 App 既有字串資源為準；設計稿的文章文字只是示意。

## 待決提案（未授權，不在本次範圍）

- 「懸浮工具」文章底部連結到 FAQ「懸浮工具沒有出現？」— 屬於新導覽行為，需要 owner 決定。
