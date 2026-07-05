package com.moooo_works.letsgogps.ui.savedlocations

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.backup.ImportPreview
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.ui.common.ImportPreviewDialog
import com.moooo_works.letsgogps.ui.pro.ProUpgradeDialog
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SavedLocationsScreen(
    onNavigateBack: () -> Unit,
    onLocationSelected: (Double, Double) -> Unit,
    onNavigateToFolderManagement: () -> Unit = {},
    viewModel: SavedLocationsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locations by viewModel.filteredLocations.collectAsStateWithLifecycle()
    val showSortTip by viewModel.showSortTip.collectAsStateWithLifecycle()
    val showFolderTip by viewModel.showFolderTip.collectAsStateWithLifecycle()

    var locationToDelete by remember { mutableStateOf<SavedLocation?>(null) }
    var locationToRename by remember { mutableStateOf<SavedLocation?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val batchSelection by viewModel.batchSelection.collectAsStateWithLifecycle()
    var showFolderPickerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val isProActive by viewModel.isProActive.collectAsStateWithLifecycle()
    val showProUpgrade by viewModel.showProUpgrade.collectAsStateWithLifecycle()
    val subscriptionOffer by viewModel.subscriptionOffer.collectAsStateWithLifecycle()
    var importExportMenuExpanded by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.parseImportData(it) { success, preview, message ->
                if (success) {
                    importPreview = preview
                    showImportDialog = true
                } else if (message == "PRO_REQUIRED") {
                    viewModel.requestProUpgrade()
                } else {
                    Toast.makeText(context, context.getString(R.string.import_failed, message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportData(it) { success, error ->
                if (success) {
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else if (error == "PRO_REQUIRED") {
                    viewModel.requestProUpgrade()
                } else {
                    Toast.makeText(context, context.getString(R.string.export_failed, error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showProUpgrade) {
        ProUpgradeDialog(
            onDismiss = { viewModel.dismissProUpgrade() },
            onWatchAd = {
                activity?.let { viewModel.watchRewardedAd(it) }
                viewModel.dismissProUpgrade()
            },
            onSubscribe = {
                activity?.let { viewModel.launchBillingFlow(it) } ?: viewModel.dismissProUpgrade()
            },
            watchAdEnabled = viewModel.watchAdEnabled(),
            subscriptionOffer = subscriptionOffer,
        )
    }

    if (showImportDialog && importPreview != null) {
        ImportPreviewDialog(
            preview = importPreview!!,
            onDismiss = { showImportDialog = false },
            onConfirm = {
                showImportDialog = false
                viewModel.applyImportData(importPreview!!) { _, message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    val canReorder = uiState.sortOption == SavedLocationsSortOption.CUSTOM &&
        uiState.query.trim().isEmpty() &&
        uiState.filter is LocationFilter.All

    var tempLocations by remember { mutableStateOf(locations) }

    LaunchedEffect(locations, canReorder) {
        tempLocations = locations
    }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            tempLocations = tempLocations.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        canDragOver = { draggedOver, dragging -> canReorder },
        onDragEnd = { _, _ ->
            if (canReorder) {
                viewModel.updateSortOrder(tempLocations)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.saved_locations_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    Box {
                        IconButton(onClick = { importExportMenuExpanded = true }) {
                            Icon(Icons.Default.ImportExport, contentDescription = stringResource(R.string.import_export_menu))
                        }
                        DropdownMenu(
                            expanded = importExportMenuExpanded,
                            onDismissRequest = { importExportMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_import_data)) },
                                trailingIcon = if (!isProActive) {
                                    { Icon(Icons.Default.Lock, contentDescription = null) }
                                } else null,
                                onClick = {
                                    importExportMenuExpanded = false
                                    if (isProActive) {
                                        importLauncher.launch(arrayOf("*/*"))
                                    } else {
                                        viewModel.requestProUpgrade()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_export_data)) },
                                trailingIcon = if (!isProActive) {
                                    { Icon(Icons.Default.Lock, contentDescription = null) }
                                } else null,
                                onClick = {
                                    importExportMenuExpanded = false
                                    if (isProActive) {
                                        val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                                        exportLauncher.launch("mockgps_locations_${dateStr}.json")
                                    } else {
                                        viewModel.requestProUpgrade()
                                    }
                                }
                            )
                        }
                    }
                    TextButton(onClick = onNavigateToFolderManagement) {
                        Text(stringResource(R.string.folder_manage_button), color = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.saved_locations_more_options))
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.saved_locations_clear_non_favorites)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    showClearConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (batchSelection.active) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    TextButton(
                        onClick = { showFolderPickerDialog = true },
                        enabled = batchSelection.selectedIds.isNotEmpty()
                    ) {
                        Text(
                            stringResource(R.string.batch_move_to_folder, batchSelection.selectedIds.size),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.exitBatchSelection() }) {
                        Text(stringResource(R.string.map_action_cancel))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜尋欄
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.saved_locations_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            // 資料夾 chip 列（可橫向捲動）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterPill(
                        label = stringResource(R.string.saved_locations_all),
                        selected = uiState.filter is LocationFilter.All,
                        onClick = { viewModel.onFilterChanged(LocationFilter.All) }
                    )
                }
                item {
                    FilterPill(
                        label = stringResource(R.string.saved_locations_favorites),
                        selected = uiState.filter is LocationFilter.Favorites,
                        onClick = { viewModel.onFilterChanged(LocationFilter.Favorites) }
                    )
                }
                items(folders) { folder ->
                    FilterPill(
                        label = folder.name,
                        selected = uiState.filter.let { it is LocationFilter.Folder && it.folderId == folder.id },
                        onClick = { viewModel.onFilterChanged(LocationFilter.Folder(folder.id, folder.name)) }
                    )
                }
            }

            // 排序列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        when (uiState.sortOption) {
                            SavedLocationsSortOption.CUSTOM -> stringResource(R.string.sort_custom)
                            SavedLocationsSortOption.RECENT -> stringResource(R.string.sort_recent)
                            SavedLocationsSortOption.NAME_ASC -> stringResource(R.string.sort_name_asc)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_custom)) },
                        onClick = {
                            viewModel.onSortOptionChanged(SavedLocationsSortOption.CUSTOM)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_recent)) },
                        onClick = {
                            viewModel.onSortOptionChanged(SavedLocationsSortOption.RECENT)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_name_asc)) },
                        onClick = {
                            viewModel.onSortOptionChanged(SavedLocationsSortOption.NAME_ASC)
                            sortMenuExpanded = false
                        }
                    )
                }
            }

            if (locations.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(stringResource(R.string.saved_locations_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.saved_locations_empty_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(state = reorderState.listState, modifier = Modifier.fillMaxSize().reorderable(reorderState)) {
                    items(tempLocations, key = { it.id }) { location ->
                        ReorderableItem(reorderState, key = location.id) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            androidx.compose.material3.Surface(
                                modifier = Modifier.fillMaxWidth(),
                                tonalElevation = elevation,
                                shadowElevation = elevation,
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Column {
                                    SavedLocationItem(
                                        location = location,
                                        canReorder = canReorder && !batchSelection.active,
                                        reorderModifier = if (!batchSelection.active) Modifier.detectReorder(reorderState) else Modifier,
                                        onClick = {
                                            if (batchSelection.active) {
                                                viewModel.toggleBatchSelection(location.id)
                                            } else {
                                                onLocationSelected(location.latitude, location.longitude)
                                            }
                                        },
                                        onLongClick = if (!batchSelection.active) {
                                            { viewModel.enterBatchSelection(location.id) }
                                        } else null,
                                        isSelected = batchSelection.selectedIds.contains(location.id),
                                        isBatchMode = batchSelection.active,
                                        onFavoriteClick = { viewModel.toggleFavorite(location) },
                                        onDeleteClick = { locationToDelete = location },
                                        onRenameClick = { locationToRename = location }
                                    )
                                    Divider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortTip) {
        SortTipCard(onDismiss = { viewModel.dismissSortTip() })
    }

    if (showFolderTip) {
        FolderTipCard(onDismiss = { viewModel.dismissFolderTip() })
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.saved_locations_clear_non_favorites)) },
            text = { Text(stringResource(R.string.saved_locations_clear_non_favorites_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearNonFavorites()
                    showClearConfirmDialog = false
                }) { Text(stringResource(R.string.map_search_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) { Text(stringResource(R.string.map_action_cancel)) }
            }
        )
    }

    if (locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { locationToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.saved_locations_delete_confirm_title)) },
            text = { Text(stringResource(R.string.saved_locations_delete_confirm_msg, locationToDelete?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    locationToDelete?.let { viewModel.deleteLocation(it) }
                    locationToDelete = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { locationToDelete = null }) { Text(stringResource(R.string.map_action_cancel)) }
            }
        )
    }

    if (locationToRename != null) {
        var newName by remember(locationToRename?.id) { mutableStateOf(locationToRename?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { locationToRename = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.saved_locations_rename)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 40) newName = it },
                    label = { Text(stringResource(R.string.map_save_location_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        locationToRename?.let { viewModel.renameLocation(it, newName) }
                        locationToRename = null
                    },
                    enabled = newName.trim().isNotEmpty()
                ) { Text(stringResource(R.string.map_action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { locationToRename = null }) { Text(stringResource(R.string.map_action_cancel)) }
            }
        )
    }

    if (showFolderPickerDialog) {
        var selectedFolderId by remember { mutableStateOf<Int?>(-1) }
        AlertDialog(
            onDismissRequest = { showFolderPickerDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.folder_move_to_title)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolderId = null }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFolderId == null,
                            onClick = { selectedFolderId = null }
                        )
                        Text(
                            stringResource(R.string.folder_uncategorized),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFolderId = folder.id }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFolderId == folder.id,
                                onClick = { selectedFolderId = folder.id }
                            )
                            Text(
                                folder.name,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedFolderId != -1) {
                            viewModel.moveBatchToFolder(selectedFolderId)
                            showFolderPickerDialog = false
                        }
                    },
                    enabled = selectedFolderId != -1
                ) {
                    Text(stringResource(R.string.map_action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderPickerDialog = false }) {
                    Text(stringResource(R.string.map_action_cancel))
                }
            }
        )
    }

}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedLocationItem(
    location: SavedLocation,
    canReorder: Boolean,
    reorderModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isBatchMode: Boolean = false,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBatchMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (location.description.isNotBlank()) {
                Text(
                    text = location.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Text(
                text = "%.4f° N, %.4f° E".format(location.latitude, location.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (location.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (location.isFavorite) stringResource(R.string.action_unfavorite) else stringResource(R.string.action_add_favorite),
                tint = if (location.isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRenameClick) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.saved_locations_rename), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canReorder) {
            IconButton(onClick = {}, modifier = reorderModifier) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FolderTipCard(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.tip_folder_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.tip_folder_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.tip_dismiss),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SortTipCard(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.tip_sort_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.tip_sort_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.tip_dismiss),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
