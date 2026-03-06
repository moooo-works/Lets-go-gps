package com.example.mockgps.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var showExportDialog by remember { mutableStateOf(false) }
    var exportSavedLocations by remember { mutableStateOf(true) }
    var exportRoutes by remember { mutableStateOf(true) }

    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.parseImportData(it) { success, preview, _ ->
                if (success) {
                    importPreview = preview
                    showImportDialog = true
                } else {
                    Toast.makeText(context, "Import failed: \$message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportDataToUri(it, exportSavedLocations, exportRoutes) { success, _ ->
                if (success) {
                    Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Export failed: \$error", Toast.LENGTH_LONG).show()
                }
            }
        }
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
                title = { Text("設定") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { showExportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export Data")
            }

            Button(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Data")
            }

            Button(
                onClick = {
                    val diagText = viewModel.generateDiagnostics()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("MockGPS Diagnostics", diagText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy Diagnostics")
            }
        }
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
        title = { Text("Export Data") },
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
                    Text("Saved Locations")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = exportRoutes,
                        onCheckedChange = onRoutesChange
                    )
                    Text("Routes")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = exportSavedLocations || exportRoutes
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text("Import Preview") },
        text = {
            Column {
                Text("Schema Version: ${preview.schemaVersion}")
                Text("Saved Locations: ${preview.savedLocationsCount}")
                Text("Routes: ${preview.routesCount}")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
