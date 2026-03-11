package com.moooo_works.letsgogps.ui.pro

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
            Text("升級至 Pro", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "解鎖所有進階功能：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "路線模擬",
                    "搖桿控制",
                    "匯入 / 匯出資料",
                    "無限儲存位置（免費版限 5 個）",
                    "移除所有廣告"
                ).forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "月費訂閱 · 前 7 天免費試用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpgrade) {
                Text("開始免費試用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍後再說")
            }
        }
    )
}
