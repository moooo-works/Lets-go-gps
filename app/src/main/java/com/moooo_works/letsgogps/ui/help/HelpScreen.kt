package com.moooo_works.letsgogps.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.moooo_works.letsgogps.R

private val helpCategories = listOf("start" to R.string.help_start, "features" to R.string.help_features, "faq" to R.string.help_faq)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(initialTopic: String = "", onBack: () -> Unit) {
    var selectedId by rememberSaveable(initialTopic) { mutableStateOf(initialTopic.takeIf { id -> helpTopics.any { it.id == id } }) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val secondary = if (dark) colors.onSurface.copy(alpha = .70f) else colors.onSurfaceVariant
    val accentText = if (dark) Color(0xFFFF8A65) else Color(0xFFC2410C)
    val accentSoft = if (dark) Color(0xFF3A2318) else Color(0xFFFFF0EA)
    val selected = helpTopics.find { it.id == selectedId }
    val back = { if (selected != null) selectedId = null else onBack() }
    BackHandler(enabled = selected != null) { selectedId = null }
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.help_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), navigationIcon = {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.help_back)) }
            })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (selected != null) {
                val paragraphs = stringResource(selected.body).split("\n\n")
                val steps = paragraphs.first().lines().map { it.replaceFirst(Regex("^\\d+\\.\\s*"), "") }
                LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = accentSoft, shape = RoundedCornerShape(6.dp)) {
                                Text(stringResource(helpCategories.first { it.first == selected.category }.second), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(stringResource(R.string.help_step_count, steps.size), color = secondary, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(selected.title), fontSize = 26.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                    if (selected.id == "floating") item { FloatingDiagram(secondary) }
                    if (selected.id == "faq_preview") item {
                        InstructionDiagram(listOf(R.string.help_diagram_select, R.string.help_diagram_preview, R.string.floating_locate), accentText, accentSoft)
                    }
                    if (selected.id == "modes") item {
                        Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                listOf(R.string.route_loop_none to "A → B → C", R.string.route_loop_loop to "A → B → C → A …", R.string.route_loop_bounce to "A → B → C → B → A …").forEach { (label, path) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(label), fontSize = 13.sp, color = secondary)
                                        Text(path, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                    }
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.help_steps_heading), Modifier.semantics { heading() }, color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    items(steps.size) { index ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.widthIn(min = 28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                StepNumber(index + 1, accentText, accentSoft)
                                if (index < steps.lastIndex) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f), contentAlignment = Alignment.Center) {
                                    Surface(color = colors.outlineVariant, modifier = Modifier.fillMaxSize()) {}
                                }
                            }
                            Text(steps[index], Modifier.weight(1f), color = colors.onSurface, fontSize = 16.sp, lineHeight = 25.sp)
                        }
                    }
                    if (paragraphs.size > 1) item {
                        Surface(color = colors.surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.CheckCircleOutline, null, Modifier.size(22.dp), tint = colors.primary)
                                    Text(stringResource(R.string.help_result_heading), color = accentText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(paragraphs.drop(1).joinToString("\n\n"), color = colors.onSurface, fontSize = 15.sp, lineHeight = 24.sp)
                            }
                        }
                    }
                    item { OutlinedButton(onClick = { selectedId = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.help_index), color = colors.onSurface) } }
                }
            } else {
                val keyword = query.trim()
                val visible = helpTopics.filter { topic ->
                    val title = stringResource(topic.title)
                    val body = stringResource(topic.body)
                    (category == "all" || topic.category == category) && (keyword.isEmpty() || title.contains(keyword, true) || body.contains(keyword, true))
                }
                LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.help_search), color = secondary) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = secondary) },
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface, unfocusedBorderColor = colors.outlineVariant, focusedBorderColor = colors.primary),
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.map_search_clear)) } })
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("all" to R.string.help_all) + helpCategories) { (id, label) ->
                                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(stringResource(label), fontWeight = if (category == id) FontWeight.Bold else FontWeight.Medium) }, shape = RoundedCornerShape(18.dp),
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentSoft, selectedLabelColor = accentText, containerColor = colors.surface, labelColor = secondary),
                                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = category == id, borderColor = colors.outlineVariant, selectedBorderColor = accentSoft),
                                    leadingIcon = if (category == id) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = accentText) } } else null)
                            }
                        }
                    }
                    if (keyword.isNotEmpty()) item { Text(stringResource(R.string.help_count, visible.size), color = secondary, fontSize = 13.sp) }
                    if (visible.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(48.dp), tint = secondary)
                            Text(stringResource(R.string.help_empty), color = colors.onSurface, fontSize = 16.sp, lineHeight = 24.sp)
                            if (category != "all") OutlinedButton(onClick = { category = "all" }) { Text(stringResource(R.string.help_all), color = accentText) }
                            if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(stringResource(R.string.map_search_clear), color = accentText) }
                        }
                    }
                    helpCategories.forEach { (group, label) ->
                        val topics = visible.filter { it.category == group }
                        if (topics.isNotEmpty()) {
                            item(key = "section_$group") {
                                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(label), Modifier.weight(1f).semantics { heading() }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = secondary)
                                    Text(stringResource(R.string.help_count, topics.size), fontSize = 13.sp, color = secondary)
                                }
                            }
                            item(key = "group_$group") {
                                Surface(color = colors.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                                    Column {
                                        topics.forEachIndexed { index, topic ->
                                            Surface(onClick = { keyboard?.hide(); selectedId = topic.id }, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                                                Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                    Box(Modifier.widthIn(min = 28.dp), contentAlignment = Alignment.Center) {
                                                        when (group) {
                                                            "start" -> StepNumber(helpTopics.filter { it.category == "start" }.indexOf(topic) + 1, accentText, accentSoft)
                                                            "faq" -> Text("Q", color = accentText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                                            else -> Icon(topicIcon(topic.id), null, Modifier.size(22.dp), tint = colors.primary)
                                                        }
                                                    }
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        val title = stringResource(topic.title)
                                                        Text(highlight(title, keyword, accentText), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                                        if (keyword.isNotEmpty() && !title.contains(keyword, true)) {
                                                            val body = stringResource(topic.body).replace('\n', ' ')
                                                            val match = body.indexOf(keyword, ignoreCase = true)
                                                            val start = (match - 24).coerceAtLeast(0)
                                                            Text(highlight(body.substring(start), keyword, accentText), fontSize = 14.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                    Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                                                }
                                            }
                                            if (index < topics.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = colors.outlineVariant)
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
}

private fun highlight(text: String, query: String, color: Color) = buildAnnotatedString {
    append(text)
    if (query.isNotEmpty()) {
        var index = text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), index, index + query.length)
            index = text.indexOf(query, startIndex = index + query.length, ignoreCase = true)
        }
    }
}

private fun topicIcon(id: String): ImageVector = when (id) {
    "floating" -> Icons.Default.PictureInPictureAlt
    "joystick" -> Icons.Default.SportsEsports
    "routes" -> Icons.Default.Route
    "modes" -> Icons.Default.Repeat
    "saved" -> Icons.Default.FolderOpen
    "steps" -> Icons.Default.DirectionsWalk
    "pro" -> Icons.Default.StarOutline
    else -> Icons.Default.HelpOutline
}

@Composable
private fun StepNumber(number: Int, foreground: Color, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Box(Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp).padding(4.dp), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InstructionDiagram(labels: List<Int>, foreground: Color, background: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            labels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StepNumber(index + 1, foreground, background)
                    Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FloatingDiagram(secondary: Color) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = colors.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Box(Modifier.fillMaxWidth().height(104.dp).padding(12.dp), contentAlignment = Alignment.CenterEnd) {
                        Surface(color = colors.primary, shape = RoundedCornerShape(50)) {
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.NearMe, null, Modifier.size(18.dp), tint = Color.White) }
                        }
                    }
                }
                Text(stringResource(R.string.floating_collapse), color = secondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ArrowForward, null, Modifier.size(18.dp), tint = colors.primary)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = colors.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().height(104.dp).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(Icons.Default.PictureInPictureAlt, null, Modifier.size(18.dp), tint = colors.primary)
                        Surface(color = colors.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = secondary)
                                Text(stringResource(R.string.help_diagram_select), Modifier.padding(start = 4.dp), fontSize = 10.sp, color = colors.onSurface)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Icons.Default.Search, Icons.Default.Bookmark, Icons.Default.Route).forEach { Icon(it, null, Modifier.size(16.dp), tint = colors.primary) }
                        }
                    }
                }
                Text(stringResource(R.string.help_diagram_expand), color = secondary, fontSize = 12.sp)
            }
        }
    }
}
