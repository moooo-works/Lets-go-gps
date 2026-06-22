package com.moooo_works.letsgogps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.domain.model.SubscriptionOffer

@Composable
fun ProSection(
    state: ProSectionState,
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    subscriptionOffer: SubscriptionOffer? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.settings_pro_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            when (state) {
                ProSectionState.Free -> ProFreeContent(onWatchAd, onSubscribe, subscriptionOffer)
                is ProSectionState.AdUnlocked -> ProUnlockedContent(state, onWatchAd, onSubscribe)
                ProSectionState.Subscribed -> ProSubscribedContent(onManageSubscription)
            }
        }
    }
}

@Composable
private fun ProFreeContent(
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
    offer: SubscriptionOffer?,
) {
    val price = offer?.formattedPrice
    // Trial disclosure must be visible at the subscription entry point even
    // before BillingClient has connected — Play reviewers open the app fresh
    // and frequently land on Settings before ProductDetails finishes loading.
    // Default to the trial wording while `offer == null` and only switch to
    // the no-trial variant when Play explicitly reports the current user has
    // exhausted trial eligibility (`hasFreeTrial == false`). Required by
    // Play's "Trial or new user offer terms" policy — the entry point must
    // spell out trial length, post-trial price, and how to cancel.
    val isTrial = offer?.hasFreeTrial != false
    val subscribeSubtitle = when {
        isTrial && price != null ->
            stringResource(R.string.settings_pro_subscribe_card_subtitle_trial_with_price, price)
        isTrial ->
            stringResource(R.string.settings_pro_subscribe_card_subtitle_trial)
        price != null ->
            stringResource(R.string.settings_pro_subscribe_card_subtitle_with_price, price)
        else ->
            stringResource(R.string.settings_pro_subscribe_card_subtitle)
    }
    val subscribeNote = stringResource(
        if (isTrial) R.string.settings_pro_subscribe_card_cancel_note_trial
        else R.string.settings_pro_subscribe_card_cancel_note
    )
    val subscribeButton = stringResource(
        if (isTrial) R.string.settings_pro_subscribe_card_button_trial
        else R.string.settings_pro_subscribe_card_button
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(IntrinsicSize.Min),
    ) {
        ProFreeCard(
            title = stringResource(R.string.settings_pro_free_card_title),
            subtitle = stringResource(R.string.settings_pro_free_card_subtitle),
            buttonText = stringResource(R.string.settings_pro_free_card_button),
            onClick = onWatchAd,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        ProFreeCard(
            title = stringResource(R.string.settings_pro_subscribe_card_title),
            subtitle = subscribeSubtitle,
            note = subscribeNote,
            buttonText = subscribeButton,
            onClick = onSubscribe,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ProFreeCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ProUnlockedContent(
    state: ProSectionState.AdUnlocked,
    onWatchAd: () -> Unit,
    onSubscribe: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_pro_unlocked_title), fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.settings_pro_unlocked_remaining, formatRemaining(state.remainingMillis)),
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { (state.remainingMillis.toFloat() / (24f * 3_600_000f)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            Button(
                onClick = onWatchAd,
                enabled = state.watchAdEnabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    stringResource(R.string.settings_pro_unlocked_watch_more),
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = onSubscribe,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Text(
                    stringResource(R.string.settings_pro_unlocked_upgrade),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (!state.watchAdEnabled) {
            Text(
                stringResource(R.string.settings_pro_unlocked_cap_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProSubscribedContent(onManageSubscription: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_pro_subscribed_title),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onManageSubscription) {
            Text(stringResource(R.string.settings_pro_subscribed_manage))
        }
    }
}

@Composable
internal fun formatRemaining(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    return when {
        totalMinutes >= 60 -> stringResource(
            R.string.settings_pro_remaining_hours_minutes,
            totalMinutes / 60,
            totalMinutes % 60
        )
        totalMinutes >= 1 -> stringResource(R.string.settings_pro_remaining_minutes, totalMinutes)
        else -> stringResource(R.string.settings_pro_remaining_less_than_minute)
    }
}
