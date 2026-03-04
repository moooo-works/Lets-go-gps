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
import androidx.compose.runtime.Composable
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


    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importDataFromUri(it) { success, message ->
                if (success) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
            viewModel.exportDataToUri(it) { success, error ->
                if (success) {
                    Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Export failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
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
                onClick = { exportLauncher.launch("mockgps_export.json") },
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
