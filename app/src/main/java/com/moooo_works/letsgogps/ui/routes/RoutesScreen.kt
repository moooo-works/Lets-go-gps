package com.moooo_works.letsgogps.ui.routes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.model.RouteSummary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoutesScreen(
    viewModel: RoutesViewModel,
    onNavigateBack: () -> Unit,
    onRouteSelected: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var routePendingDelete by remember { mutableStateOf<Int?>(null) }
    var routePendingRename by remember { mutableStateOf<Int?>(null) }
    var showMergeDialog by remember { mutableStateOf(false) }

    // System back: in selection mode, back exits selection rather than the screen.
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    // Auto-dismiss merge dialog after the merge op completes.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is RoutesEvent.MergeFinished) {
                showMergeDialog = false
            }
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.routes_selection_count, uiState.selectedRouteIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = uiState.selectedRouteIds.size >= 2,
                            onClick = { showMergeDialog = true }
                        ) {
                            Text(stringResource(R.string.routes_action_merge))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.nav_routes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (uiState.routes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(stringResource(R.string.routes_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.routes_empty_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(uiState.routes, key = { it.id }) { route ->
                    RouteCard(
                        route = route,
                        inSelectionMode = uiState.isSelectionMode,
                        isSelected = route.id in uiState.selectedRouteIds,
                        onLoadClick = { onRouteSelected(route.id) },
                        onRenameClick = { routePendingRename = route.id },
                        onDeleteClick = { routePendingDelete = route.id },
                        onLongPress = { viewModel.enterSelectionMode(route.id) },
                        onSelectionToggle = { viewModel.toggleSelection(route.id) },
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    if (routePendingRename != null) {
        val routeId = routePendingRename!!
        val initialName = uiState.routes.firstOrNull { it.id == routeId }?.name.orEmpty()
        var renameInput by rememberSaveable(routeId) { mutableStateOf(initialName) }
        AlertDialog(
            onDismissRequest = { routePendingRename = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.routes_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(R.string.routes_name_label)) },
                    singleLine = true,
                    supportingText = { Text("${renameInput.length}/40") }
                )
            },
            confirmButton = {
                val normalized = renameInput.trim()
                Button(
                    enabled = normalized.isNotEmpty() && normalized.length <= 40,
                    onClick = {
                        viewModel.renameRoute(routeId, normalized)
                        routePendingRename = null
                    }
                ) { Text(stringResource(R.string.map_action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { routePendingRename = null }) { Text(stringResource(R.string.map_action_cancel)) }
            }
        )
    }

    if (routePendingDelete != null) {
        val routeId = routePendingDelete!!
        AlertDialog(
            onDismissRequest = { routePendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.routes_delete_title)) },
            text = { Text(stringResource(R.string.routes_delete_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoute(routeId)
                        routePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { routePendingDelete = null }) { Text(stringResource(R.string.map_action_cancel)) }
            }
        )
    }

    if (showMergeDialog) {
        val initial = remember(uiState.selectedRouteIds) { viewModel.buildDefaultMergedName() }
        var mergeName by rememberSaveable(initial) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.routes_merge_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.routes_selection_count, uiState.selectedRouteIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = mergeName,
                        onValueChange = { mergeName = it },
                        label = { Text(stringResource(R.string.routes_merge_name_label)) },
                        singleLine = true,
                        supportingText = { Text("${mergeName.length}/40") }
                    )
                }
            },
            confirmButton = {
                val normalized = mergeName.trim()
                Button(
                    enabled = normalized.isNotEmpty() && normalized.length <= 40,
                    onClick = { viewModel.mergeSelected(normalized) }
                ) { Text(stringResource(R.string.routes_action_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text(stringResource(R.string.map_action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteCard(
    route: RouteSummary,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onLoadClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLongPress: () -> Unit,
    onSelectionToggle: () -> Unit,
) {
    val containerColor = when {
        inSelectionMode && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (inSelectionMode) onSelectionToggle() },
                onLongClick = { if (!inSelectionMode) onLongPress() },
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (inSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onSelectionToggle() })
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.routes_point_count, route.pointCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!inSelectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onLoadClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text(stringResource(R.string.routes_action_load)) }
                        OutlinedButton(
                            onClick = onRenameClick,
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.saved_locations_rename)) }
                        OutlinedButton(
                            onClick = onDeleteClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
}
