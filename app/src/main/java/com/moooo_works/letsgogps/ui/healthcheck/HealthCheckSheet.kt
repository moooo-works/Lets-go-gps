package com.moooo_works.letsgogps.ui.healthcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckItem
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState
import com.moooo_works.letsgogps.domain.healthcheck.ItemStatus

private val GREEN = Color(0xFF22C55E)
private val ORANGE = Color(0xFFF97316)
private val AMBER = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthCheckSheet(
    state: HealthCheckState,
    onItemFix: (HealthCheckItem) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        HealthCheckSheetContent(
            state = state,
            onItemFix = onItemFix,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun HealthCheckSheetContent(
    state: HealthCheckState,
    onItemFix: (HealthCheckItem) -> Unit,
    onRefresh: () -> Unit,
) {
    val failedCount = state.items.count { (item, status) ->
        item.isCritical && status is ItemStatus.Failed
    }
    val allCriticalPassed = failedCount == 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.healthcheck_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (allCriticalPassed) {
                stringResource(R.string.healthcheck_subtitle_all_passed)
            } else {
                stringResource(R.string.healthcheck_subtitle_with_failures, failedCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        HealthCheckItem.values().forEach { item ->
            val status = state.statusOf(item)
            if (status is ItemStatus.NotApplicable) return@forEach
            HealthCheckItemRow(
                item = item,
                status = status,
                onFix = { onItemFix(item) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.healthcheck_action_refresh))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HealthCheckItemRow(
    item: HealthCheckItem,
    status: ItemStatus,
    onFix: () -> Unit,
) {
    val labelRes = item.labelRes()
    val descRes = item.descRes()
    val (iconVector, iconTint) = when {
        status is ItemStatus.Passed -> Icons.Default.CheckCircle to GREEN
        !item.isCritical && status is ItemStatus.Failed -> Icons.Default.WarningAmber to AMBER
        else -> Icons.Default.ErrorOutline to ORANGE
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status is ItemStatus.Failed) {
                TextButton(onClick = onFix) {
                    Text(stringResource(R.string.healthcheck_action_fix))
                }
            }
        }
    }
}

private fun HealthCheckItem.labelRes(): Int = when (this) {
    HealthCheckItem.NotificationPermission -> R.string.healthcheck_item_notification
    HealthCheckItem.LocationPermission -> R.string.healthcheck_item_location
    HealthCheckItem.GpsEnabled -> R.string.healthcheck_item_gps
    HealthCheckItem.DeveloperMode -> R.string.healthcheck_item_dev_mode
    HealthCheckItem.MockAppSelected -> R.string.healthcheck_item_mock_app
    HealthCheckItem.BatteryOptimizationExempt -> R.string.healthcheck_item_battery
}

private fun HealthCheckItem.descRes(): Int = when (this) {
    HealthCheckItem.NotificationPermission -> R.string.healthcheck_item_notification_desc
    HealthCheckItem.LocationPermission -> R.string.healthcheck_item_location_desc
    HealthCheckItem.GpsEnabled -> R.string.healthcheck_item_gps_desc
    HealthCheckItem.DeveloperMode -> R.string.healthcheck_item_dev_mode_desc
    HealthCheckItem.MockAppSelected -> R.string.healthcheck_item_mock_app_desc
    HealthCheckItem.BatteryOptimizationExempt -> R.string.healthcheck_item_battery_desc
}

/**
 * Dispatches the per-item fix action: app-level permissions (location,
 * notification) trigger the in-app permission request; everything else
 * launches the appropriate system Settings intent.
 *
 * Falls back to the top-level Settings screen if a deep intent isn't
 * available on the device (some OEMs).
 */
fun handleHealthCheckFix(
    item: HealthCheckItem,
    context: android.content.Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    when (item) {
        HealthCheckItem.LocationPermission -> permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
        HealthCheckItem.NotificationPermission -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        HealthCheckItem.GpsEnabled -> safeStart(
            context, android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        )
        HealthCheckItem.DeveloperMode -> safeStart(
            context, android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS)
        )
        HealthCheckItem.MockAppSelected -> safeStart(
            context, android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )
        HealthCheckItem.BatteryOptimizationExempt -> {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            safeStart(context, intent)
        }
    }
}

private fun safeStart(context: android.content.Context, intent: android.content.Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
}
