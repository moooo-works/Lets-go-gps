package com.moooo_works.letsgogps.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.backup.ImportPreview

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_preview_format, preview.format))
                Text(stringResource(R.string.import_preview_locations, preview.savedLocationsCount))
                Text(stringResource(R.string.import_preview_routes, preview.routesCount))
                if (preview.foldersCount > 0) {
                    Text(stringResource(R.string.import_preview_folders, preview.foldersCount))
                }
                if (preview.hasSettings) {
                    Text(stringResource(R.string.import_preview_settings))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_import_data))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.map_action_cancel))
            }
        }
    )
}
