package com.moooo_works.letsgogps.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R

/**
 * 步數同步的計次閘門對話框。
 *
 * 只在「非訂閱者 + 次數為 0 + 步數同步開關已開」時出現。
 * 訂閱者永遠看不到這個對話框。
 *
 * 預設退路是「這次不用步數同步」——次數不足**不得**擋住位置模擬本身。
 */
@Composable
fun StepSyncCreditDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onStartWithoutStepSync: () -> Unit,
    onSubscribe: () -> Unit,
    adUnavailable: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Icon(
                Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                stringResource(R.string.step_credit_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.step_credit_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (adUnavailable) {
                    Text(
                        stringResource(R.string.step_credit_dialog_ad_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 訂閱入口放在內容區，不放 action 列。
                // M3 的 AlertDialog 用 FlowRow 排 confirm/dismiss，塞三顆或塞
                // Column 進去會算錯高度，第三顆按鈕會被對話框底部裁掉（實機驗證過）。
                TextButton(
                    onClick = onSubscribe,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.step_credit_dialog_action_subscribe))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onWatchAd) {
                Text(stringResource(R.string.step_credit_dialog_action_watch_ad))
            }
        },
        dismissButton = {
            TextButton(onClick = onStartWithoutStepSync) {
                Text(stringResource(R.string.step_credit_dialog_action_skip))
            }
        }
    )
}
