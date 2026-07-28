package com.moooo_works.letsgogps.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.health.HealthConnectAvailability

/**
 * 設定頁的步數同步區塊。
 *
 * 這裡**不做**任何 Pro 閘門——開關人人可開，只表達「我想用」。
 * 實際的計次扣款發生在按下開始模擬時（見 `ui/map/StepSyncGate.kt`）。
 */
@Composable
fun StepSyncSection(viewModel: SettingsViewModel) {
    val enabled by viewModel.stepSyncEnabled.collectAsState()
    val stepLength by viewModel.stepLengthMeters.collectAsState()
    val dailyQuota by viewModel.stepDailyQuota.collectAsState()
    val usedToday by viewModel.stepQuotaUsedToday.collectAsState()
    val isSubscriber by viewModel.isSubscriptionActive.collectAsState()
    val credits by viewModel.featureCredits.collectAsState()
    val hasPermission by viewModel.hasHealthPermission.collectAsState()

    // 每次回到前景都重查：使用者可能剛在系統設定裡撤銷權限或安裝
    // Health Connect，快取住會顯示過期狀態。
    val lifecycleOwner = LocalLifecycleOwner.current
    var statusRefreshToken by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statusRefreshToken++
                viewModel.refreshHealthPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val status = remember(statusRefreshToken) { viewModel.healthConnectStatus() }
    val fitInstalled = remember(statusRefreshToken) { viewModel.isGoogleFitInstalled() }

    var showSetupGuide by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = remember { viewModel.requestHealthPermissionsContract() }
    ) { viewModel.refreshHealthPermission() }

    if (showSetupGuide) {
        StepSyncSetupDialog(
            onDismiss = { showSetupGuide = false },
            healthConnectReady = status == HealthConnectAvailability.Status.Available,
            writePermissionGranted = hasPermission,
            googleFitInstalled = fitInstalled,
            onOpenHealthConnectStore = { viewModel.openHealthConnectPlayStore() },
            onGrantPermission = {
                permissionLauncher.launch(viewModel.healthConnectWritePermissions)
            },
            onOpenHealthConnectSettings = { viewModel.openHealthConnectSettings() },
            onInstallGoogleFit = { viewModel.openGoogleFitPlayStore() },
            onOpenGoogleFit = { viewModel.openGoogleFit() },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.settings_step_sync_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (status) {
                HealthConnectAvailability.Status.Unsupported -> StatusLine(
                    text = stringResource(R.string.settings_step_sync_unsupported)
                )

                HealthConnectAvailability.Status.UpdateRequired -> StatusLine(
                    text = stringResource(R.string.settings_step_sync_update_required),
                    actionLabel = stringResource(R.string.settings_step_sync_open_store),
                    onAction = { viewModel.openHealthConnectPlayStore() }
                )

                HealthConnectAvailability.Status.Available -> {
                    if (!hasPermission) {
                        StatusLine(
                            text = stringResource(R.string.settings_step_sync_needs_permission),
                            actionLabel = stringResource(R.string.settings_step_sync_grant),
                            onAction = {
                                permissionLauncher.launch(viewModel.healthConnectWritePermissions)
                            }
                        )
                    } else {
                        StatusLine(text = stringResource(R.string.settings_step_sync_connected))
                    }
                }
            }

            val controlsUsable =
                status == HealthConnectAvailability.Status.Available && hasPermission

            // Google Fit 是鏈路的必要中繼——沒有它，步數只會停在 Health Connect
            // 傳不到目標 app。缺了就明講並提供安裝入口。
            if (!fitInstalled) {
                StatusLine(
                    text = stringResource(R.string.settings_step_sync_fit_missing),
                    actionLabel = stringResource(R.string.step_setup_action_install_fit),
                    onAction = { viewModel.openGoogleFitPlayStore() }
                )
            }

            // 設定說明入口。四段鏈路的設定分散在四個不同 app，狀態列講不完。
            StatusLine(
                text = stringResource(R.string.settings_step_sync_setup_hint),
                actionLabel = stringResource(R.string.settings_step_sync_setup_open),
                onAction = { showSetupGuide = true }
            )

            SwitchRow(
                title = stringResource(R.string.settings_step_sync_enable),
                subtitle = stringResource(R.string.settings_step_sync_enable_desc),
                checked = enabled && controlsUsable,
                enabled = controlsUsable,
                onCheckedChange = { viewModel.setStepSyncEnabled(it) }
            )

            if (enabled && controlsUsable) {
                NumberField(
                    label = stringResource(R.string.settings_step_length),
                    initial = stepLength.toString(),
                    onCommit = { text -> text.toDoubleOrNull()?.let(viewModel::setStepLengthMeters) }
                )

                NumberField(
                    label = stringResource(R.string.settings_step_quota),
                    initial = dailyQuota.toString(),
                    onCommit = { text -> text.toIntOrNull()?.let(viewModel::setStepDailyQuota) }
                )

                Text(
                    stringResource(R.string.settings_step_quota_used, usedToday, dailyQuota),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (isSubscriber) {
                        stringResource(R.string.settings_step_credits_unlimited)
                    } else {
                        stringResource(R.string.settings_step_credits_remaining, credits)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/**
 * 數字輸入欄。編輯中保留使用者的原始字串（含中途的空字串），
 * 只在能解析成合法數字時才寫進 DataStore——否則刪光重打會被立刻覆寫。
 */
@Composable
private fun NumberField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onCommit(it)
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
    }
}
