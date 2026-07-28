package com.moooo_works.letsgogps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.domain.health.StepAccumulator

/**
 * 步數同步的設定說明。
 *
 * 這條鏈路有四段，任一段沒接上步數就傳不到目標 app，而且每段的設定位置都不同
 * （本 app／系統 Health Connect／Google Fit 內部／目標 app 內部），
 * 光靠設定頁的一行狀態列講不清楚，所以獨立成一頁逐步說明。
 *
 * 能偵測的步驟顯示即時狀態並提供跳轉按鈕；無法偵測的（Fit 內部授權、
 * 目標 app 的步數來源）只能給文字指路。
 */
@Composable
fun StepSyncSetupDialog(
    onDismiss: () -> Unit,
    healthConnectReady: Boolean,
    writePermissionGranted: Boolean,
    googleFitInstalled: Boolean,
    onOpenHealthConnectStore: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
    onInstallGoogleFit: () -> Unit,
    onOpenGoogleFit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.step_setup_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    stringResource(R.string.step_setup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringResource(R.string.step_setup_chain),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                SetupStep(
                    index = 1,
                    done = healthConnectReady,
                    title = stringResource(R.string.step_setup_1_title),
                    body = stringResource(R.string.step_setup_1_body),
                    actionLabel = if (healthConnectReady) null
                    else stringResource(R.string.settings_step_sync_open_store),
                    onAction = onOpenHealthConnectStore,
                )

                SetupStep(
                    index = 2,
                    done = writePermissionGranted,
                    title = stringResource(R.string.step_setup_2_title),
                    body = stringResource(R.string.step_setup_2_body),
                    actionLabel = if (writePermissionGranted) null
                    else stringResource(R.string.settings_step_sync_grant),
                    onAction = onGrantPermission,
                )

                SetupStep(
                    index = 3,
                    done = googleFitInstalled,
                    title = stringResource(R.string.step_setup_3_title),
                    body = stringResource(R.string.step_setup_3_body),
                    actionLabel = if (googleFitInstalled) null
                    else stringResource(R.string.step_setup_action_install_fit),
                    onAction = onInstallGoogleFit,
                )

                // 第 4 步無法偵測：Fit 的「與健康資料同步平台保持同步」授權狀態
                // 沒有對外 API 可查，只能給指路 + 跳轉。
                SetupStep(
                    index = 4,
                    done = false,
                    showUncheckedAsNumber = true,
                    title = stringResource(R.string.step_setup_4_title),
                    body = stringResource(R.string.step_setup_4_body),
                    actionLabel = if (googleFitInstalled)
                        stringResource(R.string.step_setup_action_open_fit) else null,
                    onAction = onOpenGoogleFit,
                )

                SetupStep(
                    index = 5,
                    done = false,
                    showUncheckedAsNumber = true,
                    title = stringResource(R.string.step_setup_5_title),
                    body = stringResource(R.string.step_setup_5_body),
                    actionLabel = null,
                    onAction = {},
                )

                // 速度上限：自行車（15）在範圍內、開車（40）不在，預設模式之間
                // 的差異不講清楚使用者不會知道。
                Text(
                    stringResource(
                        R.string.step_setup_speed_limit_note,
                        StepAccumulator.MAX_STEP_SPEED_KMH.toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringResource(R.string.step_setup_troubleshoot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Fit 讀取後會把同一批步數回寫 Health Connect，導致 HC 的明細
                // 每個時段出現兩筆、總數看似兩倍。實機驗證確認過，先講清楚
                // 免得使用者以為是重複寫入的 bug。
                Text(
                    stringResource(R.string.step_setup_duplicate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onOpenHealthConnectSettings) {
                    Text(stringResource(R.string.step_setup_action_open_hc_settings))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun SetupStep(
    index: Int,
    done: Boolean,
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    showUncheckedAsNumber: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepBadge(index = index, done = done, showUncheckedAsNumber = showUncheckedAsNumber)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 0.dp,
                        vertical = 4.dp
                    )
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StepBadge(index: Int, done: Boolean, showUncheckedAsNumber: Boolean) {
    val bg = when {
        done -> MaterialTheme.colorScheme.primary
        showUncheckedAsNumber -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (showUncheckedAsNumber) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}
