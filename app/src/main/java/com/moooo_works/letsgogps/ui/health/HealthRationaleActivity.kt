package com.moooo_works.letsgogps.ui.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.ui.theme.MockGpsTheme

/**
 * Health Connect 權限用途說明頁。
 *
 * 由 Health Connect 在使用者點「隱私權政策」時啟動，是 Play 審核的必要元件。
 * 純靜態頁面，不注入任何東西——它可能在 app 主流程之外被獨立啟動。
 *
 * ponytail: 主題固定跟隨系統，不去讀使用者的主題偏好。這頁一年被打開不到一次，
 * 為它拉一整條 DataStore 依賴不划算。
 */
class HealthRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MockGpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RationaleContent(onClose = { finish() })
                }
            }
        }
    }
}

@Composable
private fun RationaleContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.health_rationale_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Section(R.string.health_rationale_what_title, R.string.health_rationale_what_body)
        Section(R.string.health_rationale_why_title, R.string.health_rationale_why_body)
        Section(R.string.health_rationale_read_title, R.string.health_rationale_read_body)
        Section(R.string.health_rationale_control_title, R.string.health_rationale_control_body)

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.health_rationale_close))
        }
    }
}

@Composable
private fun Section(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
