package com.moooo_works.letsgogps.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(initialTopic: String = "", onBack: () -> Unit) {
    var selectedId by rememberSaveable(initialTopic) { mutableStateOf(initialTopic.takeIf { id -> helpTopics.any { it.id == id } }) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    val keyboard = LocalSoftwareKeyboardController.current
    val selected = helpTopics.find { it.id == selectedId }
    val back = { if (selected != null) selectedId = null else onBack() }
    BackHandler(enabled = selected != null) { selectedId = null }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.help_title)) }, navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.help_back)) }
        })
    }) { padding ->
        if (selected != null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { Text(stringResource(selected.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                if (selected.id == "floating" || selected.id == "faq_preview") item {
                    InstructionDiagram(listOf(R.string.help_diagram_select, R.string.help_diagram_preview, R.string.floating_locate))
                }
                if (selected.id == "modes") item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.route_loop_none) + "  A → B → C")
                            Text(stringResource(R.string.route_loop_loop) + "  A → B → C → A …")
                            Text(stringResource(R.string.route_loop_bounce) + "  A → B → C → B → A …")
                        }
                    }
                }
                item { Text(stringResource(selected.body), style = MaterialTheme.typography.bodyLarge) }
                item { TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.help_index)) } }
            }
        } else {
            val visible = helpTopics.filter { topic ->
                val title = stringResource(topic.title)
                val body = stringResource(topic.body)
                (category == "all" || topic.category == category) &&
                    (query.isBlank() || title.contains(query.trim(), ignoreCase = true) || body.contains(query.trim(), ignoreCase = true))
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    label = { Text(stringResource(R.string.help_search)) }, singleLine = true,
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.map_search_clear)) } })
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to R.string.help_all, "start" to R.string.help_start, "features" to R.string.help_features, "faq" to R.string.help_faq).forEach { (id, label) ->
                        FilterChip(selected = category == id, onClick = { category = id }, label = { Text(stringResource(label)) })
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visible.isEmpty()) item { Text(stringResource(R.string.help_empty), style = MaterialTheme.typography.bodyLarge) }
                    items(visible, key = { it.id }) { topic ->
                        Card(onClick = { keyboard?.hide(); selectedId = topic.id }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(topic.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Icon(Icons.Default.KeyboardArrowRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionDiagram(labels: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text("${index + 1}  →  ${stringResource(label)}", Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
