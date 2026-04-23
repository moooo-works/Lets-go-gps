package com.moooo_works.letsgogps.ui.pro

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R

@Composable
fun ProUpgradeDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(stringResource(R.string.pro_dialog_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pro_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    stringResource(R.string.pro_dialog_feature_routes),
                    stringResource(R.string.pro_dialog_feature_joystick),
                    stringResource(R.string.pro_dialog_feature_export),
                    stringResource(R.string.pro_dialog_feature_ads)
                ).forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    stringResource(R.string.pro_dialog_cancel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpgrade) {
                Text(stringResource(R.string.pro_dialog_action_upgrade))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.map_action_cancel))
            }
        }
    )
}
