package com.moooo_works.letsgogps.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import kotlinx.coroutines.launch

private const val TOTAL_STEPS = 3

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { TOTAL_STEPS })
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            Text(
                text = stringResource(R.string.onboarding_step_of, pagerState.currentPage + 1, TOTAL_STEPS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                OnboardingPage(page = page, onActionClick = { intent ->
                    context.startActivity(intent)
                })
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dot indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(TOTAL_STEPS) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            val isLastPage = pagerState.currentPage == TOTAL_STEPS - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        onDismiss()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isLastPage) stringResource(R.string.onboarding_done)
                           else stringResource(R.string.onboarding_next)
                )
            }

            if (!isLastPage) {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int, onActionClick: (Intent) -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val emoji = when (page) {
            0 -> "📍"
            1 -> "🔧"
            else -> "✅"
        }
        Text(text = emoji, style = MaterialTheme.typography.displayMedium)

        Text(
            text = when (page) {
                0 -> stringResource(R.string.onboarding_step1_title)
                1 -> stringResource(R.string.onboarding_step2_title)
                else -> stringResource(R.string.onboarding_step3_title)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Text(
            text = when (page) {
                0 -> stringResource(R.string.onboarding_step1_desc)
                1 -> stringResource(R.string.onboarding_step2_desc)
                else -> stringResource(R.string.onboarding_step3_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (page == 1) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = {
                onActionClick(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }) {
                Text(stringResource(R.string.onboarding_step2_action))
            }
        }

        if (page == 2) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = {
                onActionClick(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }) {
                Text(stringResource(R.string.onboarding_step3_action))
            }
        }
    }
}
