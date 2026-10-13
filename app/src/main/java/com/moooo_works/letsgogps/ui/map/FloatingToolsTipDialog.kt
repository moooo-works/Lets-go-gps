package com.moooo_works.letsgogps.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moooo_works.letsgogps.R

@Composable
internal fun FloatingToolsTipDialog(onDismiss: () -> Unit, onReadGuide: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.tip_floating_tools_title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.tip_floating_tools_body), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                TextButton(onClick = { onDismiss(); onReadGuide() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.help_read_guide))
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tip_dismiss), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
