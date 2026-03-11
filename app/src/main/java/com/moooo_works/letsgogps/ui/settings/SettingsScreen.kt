package com.moooo_works.letsgogps.ui.settings

import com.moooo_works.letsgogps.ui.pro.ProUpgradeDialog
import com.moooo_works.letsgogps.ui.theme.ThemePreference
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val mockPermissionStatus by viewModel.mockPermissionStatus.collectAsState()
    val isProActive by viewModel.isProActive.collectAsState()
    val showProUpgrade by viewModel.showProUpgrade.collectAsState()
    val altitude by viewModel.altitude.collectAsState()
    val randomAltitude by viewModel.randomAltitude.collectAsState()
    val coordinateJitter by viewModel.coordinateJitter.collectAsState()

    var altitudeInput by remember(altitude) { mutableStateOf(altitude.toString()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshMockPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showClearNonFavoritesDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportSavedLocations by remember { mutableStateOf(true) }
    var exportRoutes by remember { mutableStateOf(true) }

    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.parseImportData(it) { success, preview, message ->
                if (success) {
                    importPreview = preview
                    showImportDialog = true
                } else if (message == "PRO_REQUIRED") {
                    viewModel.requestProUpgrade()
                } else {
                    Toast.makeText(context, "Import failed: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportDataToUri(it, exportSavedLocations, exportRoutes) { success, error ->
                if (success) {
                    Toast.makeText(context, "匯出成功", Toast.LENGTH_SHORT).show()
                } else if (error == "PRO_REQUIRED") {
                    viewModel.requestProUpgrade()
                } else {
                    Toast.makeText(context, "匯出失敗：$error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showProUpgrade) {
        ProUpgradeDialog(
            onDismiss = { viewModel.dismissProUpgrade() },
            onUpgrade = { activity?.let { viewModel.launchBillingFlow(it) } ?: viewModel.dismissProUpgrade() }
        )
    }

    if (showExportDialog) {
        ExportOptionsDialog(
            exportSavedLocations = exportSavedLocations,
            onSavedLocationsChange = { exportSavedLocations = it },
            exportRoutes = exportRoutes,
            onRoutesChange = { exportRoutes = it },
            onDismiss = { showExportDialog = false },
            onConfirm = {
                showExportDialog = false
                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                exportLauncher.launch("mockgps_export_${dateStr}.json")
            }
        )
    }

    if (showImportDialog && importPreview != null) {
        ImportPreviewDialog(
            preview = importPreview!!,
            onDismiss = { showImportDialog = false },
            onConfirm = {
                showImportDialog = false
                viewModel.applyImportData(importPreview!!) { _, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "設定",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 權限狀態卡片
            val (dotColor, statusLabel, statusDesc) = when (mockPermissionStatus) {
                is MockPermissionStatus.Allowed -> Triple(
                    Color(0xFF22C55E),
                    "Mock Location 權限",
                    "已授權 — 可正常使用模擬功能"
                )
                is MockPermissionStatus.NotAllowed -> Triple(
                    Color(0xFFF97316),
                    "Mock Location 權限",
                    "未授權 — 請到開發者選項選擇此 App"
                )
                is MockPermissionStatus.CheckFailed -> Triple(
                    Color(0xFFEF4444),
                    "Mock Location 權限",
                    "權限檢查失敗"
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            statusDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (mockPermissionStatus is MockPermissionStatus.NotAllowed) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            )
                        }) {
                            Text("開發者選項", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 外觀主題
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "外觀主題",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemePreference.SYSTEM to "跟隨系統",
                            ThemePreference.LIGHT  to "淺色",
                            ThemePreference.DARK   to "深色"
                        ).forEach { (pref, label) ->
                            val selected = themePreference == pref
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onThemeChange(pref) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 模擬設定
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "模擬設定",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("預設高度 (公尺)", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = altitudeInput,
                            onValueChange = { 
                                altitudeInput = it
                                it.toDoubleOrNull()?.let { value -> viewModel.setAltitude(value) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("高度隨機化", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "在基礎高度上加入 ±0.5m 的波動",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = randomAltitude,
                            onCheckedChange = { viewModel.setRandomAltitude(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("座標隨機抖動", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "在經緯度上加入微小偏移 (1-5m)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = coordinateJitter,
                            onCheckedChange = { viewModel.setCoordinateJitter(it) }
                        )
                    }
                }
            }

            // 功能選單
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                SettingsMenuItem(
                    label = "匯出資料",
                    locked = !isProActive,
                    onClick = {
                        if (!isProActive) viewModel.requestProUpgrade()
                        else showExportDialog = true
                    }
                )
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsMenuItem(
                    label = "匯入資料",
                    locked = !isProActive,
                    onClick = {
                        if (!isProActive) viewModel.requestProUpgrade()
                        else importLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                )
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsMenuItem(
                    label = "複製診斷資訊",
                    onClick = {
                        val diagText = viewModel.generateDiagnostics()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("MockGPS Diagnostics", diagText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 資料管理
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                SettingsMenuItem(
                    label = "清除非最愛位置",
                    onClick = { showClearNonFavoritesDialog = true }
                )
            }

            // 應用程式資訊
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                SettingsMenuItem(
                    label = "開發者選項",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    }
                )
                Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsMenuItem(
                    label = "隱私政策",
                    onClick = {
                        val url = "https://moooo-works.github.io/letsgogps-privacy"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }

            if (showClearNonFavoritesDialog) {
                AlertDialog(
                    onDismissRequest = { showClearNonFavoritesDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    title = { Text("清除非最愛位置") },
                    text = { Text("確定要刪除所有未設為最愛的儲存位置？此操作無法復原。") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.clearNonFavorites()
                            showClearNonFavoritesDialog = false
                        }) { Text("清除", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearNonFavoritesDialog = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(label: String, locked: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        Icon(
            if (locked) Icons.Default.Lock else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportOptionsDialog(
    exportSavedLocations: Boolean,
    onSavedLocationsChange: (Boolean) -> Unit,
    exportRoutes: Boolean,
    onRoutesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text("匯出資料") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = exportSavedLocations,
                        onCheckedChange = onSavedLocationsChange
                    )
                    Text("儲存位置")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = exportRoutes,
                        onCheckedChange = onRoutesChange
                    )
                    Text("路線")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = exportSavedLocations || exportRoutes
            ) {
                Text("匯出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text("匯入預覽") },
        text = {
            Column {
                Text("資料版本：${preview.schemaVersion}")
                Text("儲存位置：${preview.savedLocationsCount}")
                Text("路線：${preview.routesCount}")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("匯入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
