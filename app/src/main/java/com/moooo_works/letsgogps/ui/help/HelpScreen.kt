package com.moooo_works.letsgogps.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R

private val helpCategories = listOf("start" to R.string.help_start, "features" to R.string.help_features, "faq" to R.string.help_faq)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(initialTopic: String = "", onBack: () -> Unit) {
    var selectedId by rememberSaveable(initialTopic) { mutableStateOf(initialTopic.takeIf { id -> helpTopics.any { it.id == id } }) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val selected = helpTopics.find { it.id == selectedId }
    val back = { if (selected != null) selectedId = null else onBack() }
    BackHandler(enabled = selected != null) { selectedId = null }
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.help_back)) }
            })
    }) { padding ->
        if (selected != null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TopicIcon(selected.id)
                        Text(stringResource(helpCategories.first { it.first == selected.category }.second), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(selected.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                }
                if (selected.id == "floating" || selected.id == "faq_preview") item {
                    InstructionDiagram(listOf(R.string.help_diagram_select, R.string.help_diagram_preview, R.string.floating_locate))
                }
                if (selected.id == "modes") item {
                    Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            listOf(R.string.route_loop_none to "A → B → C", R.string.route_loop_loop to "A → B → C → A …", R.string.route_loop_bounce to "A → B → C → B → A …").forEach { (label, path) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                                    Text(path, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                }
                            }
                        }
                    }
                }
                item { Text(stringResource(selected.body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface) }
                item { OutlinedButton(onClick = { selectedId = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.help_index), color = colors.onSurface) } }
            }
        } else {
            val visible = helpTopics.filter { topic ->
                val title = stringResource(topic.title)
                val body = stringResource(topic.body)
                (category == "all" || topic.category == category) &&
                    (query.isBlank() || title.contains(query.trim(), ignoreCase = true) || body.contains(query.trim(), ignoreCase = true))
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(stringResource(R.string.help_intro), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text(stringResource(R.string.help_search)) }, singleLine = true, shape = MaterialTheme.shapes.large,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.onSurfaceVariant) },
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface,
                        unfocusedBorderColor = colors.outlineVariant, focusedBorderColor = colors.primary),
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.map_search_clear)) } })
                FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("all" to R.string.help_all) + helpCategories).forEach { (id, label) ->
                        FilterChip(selected = category == id, onClick = { category = id }, label = { Text(stringResource(label)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary.copy(alpha = .12f), selectedLabelColor = colors.onSurface,
                                containerColor = colors.surface, labelColor = colors.onSurfaceVariant),
                            leadingIcon = if (category == id) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = colors.primary) } } else null)
                    }
                }
                Text(stringResource(R.string.help_count, visible.size), Modifier.padding(horizontal = 20.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visible.isEmpty()) item { Text(stringResource(R.string.help_empty), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
                    helpCategories.forEach { (group, label) ->
                        val topics = visible.filter { it.category == group }
                        if (topics.isNotEmpty()) {
                            item(key = "section_$group") {
                                Text(stringResource(label), Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
                            }
                            items(topics, key = { it.id }) { topic ->
                                Surface(onClick = { keyboard?.hide(); selectedId = topic.id }, modifier = Modifier.fillMaxWidth(),
                                    color = colors.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .6f))) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        TopicIcon(topic.id)
                                        Text(stringResource(topic.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                        Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicIcon(id: String) {
    val icon: ImageVector = when (id) {
        "setup" -> Icons.Default.Settings
        "start" -> Icons.Default.Place
        "search" -> Icons.Default.Search
        "floating" -> Icons.Default.NearMe
        "joystick" -> Icons.Default.ControlCamera
        "routes" -> Icons.Default.Route
        "modes" -> Icons.Default.Repeat
        "saved" -> Icons.Default.Bookmark
        "steps" -> Icons.Default.DirectionsWalk
        "pro" -> Icons.Default.Star
        else -> Icons.Default.HelpOutline
    }
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), shape = MaterialTheme.shapes.medium) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun InstructionDiagram(labels: List<Int>) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            labels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = colors.primary.copy(alpha = .12f), shape = MaterialTheme.shapes.small) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onSurface) }
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
        }
    }
}
