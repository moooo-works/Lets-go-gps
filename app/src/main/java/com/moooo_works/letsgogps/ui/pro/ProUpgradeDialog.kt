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
import com.moooo_works.letsgogps.domain.model.SubscriptionOffer

@Composable
fun ProUpgradeDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
    watchAdEnabled: Boolean = true,
    subscriptionOffer: SubscriptionOffer? = null,
) {
    val price = subscriptionOffer?.formattedPrice
    // Default to the trial wording while `subscriptionOffer == null` (Billing
    // hasn't connected yet) — Play reviewers may trigger the dialog before
    // ProductDetails loads, and the trial terms must be visible at every
    // subscription entry point per Play policy. Only switch to the no-trial
    // variant when Play explicitly reports the current user has exhausted
    // trial eligibility.
    val isTrial = subscriptionOffer?.hasFreeTrial != false
    val descText = when {
        isTrial && price != null -> stringResource(R.string.pro_dialog_desc_with_price, price)
        isTrial -> stringResource(R.string.pro_dialog_desc)
        price != null -> stringResource(R.string.pro_dialog_desc_no_trial_with_price, price)
        else -> stringResource(R.string.pro_dialog_desc_no_trial)
    }
    val cancelNote = stringResource(
        if (isTrial) R.string.pro_dialog_cancel_note
        else R.string.pro_dialog_cancel_note_no_trial
    )
    val subscribeButton = stringResource(
        if (isTrial) R.string.pro_dialog_action_upgrade
        else R.string.pro_dialog_action_subscribe
    )
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
                    text = descText,
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
                    cancelNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onWatchAd,
                    enabled = watchAdEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pro_dialog_action_watch_ad))
                }
                Button(
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(subscribeButton)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.map_action_cancel))
            }
        }
    )
}
