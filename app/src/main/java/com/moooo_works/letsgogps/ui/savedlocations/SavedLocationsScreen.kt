package com.moooo_works.letsgogps.ui.savedlocations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.model.SavedLocation

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SavedLocationsScreen(
    onNavigateBack: () -> Unit,
    onLocationSelected: (Double, Double) -> Unit,
    viewModel: SavedLocationsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locations by viewModel.filteredLocations.collectAsStateWithLifecycle()

    var locationToDelete by remember { mutableStateOf<SavedLocation?>(null) }
    var locationToRename by remember { mutableStateOf<SavedLocation?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

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

            // 篩選 + 排序列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全部 pill
                FilterPill(
                    label = stringResource(R.string.saved_locations_all),
                    selected = uiState.showHistory && uiState.showFavorites,
                    onClick = {
                        viewModel.onShowHistoryChanged(true)
                        viewModel.onShowFavoritesChanged(true)
                    }
                )
                // 我的最愛 pill
                FilterPill(
                    label = stringResource(R.string.saved_locations_favorites),
                    selected = !uiState.showHistory && uiState.showFavorites,
                    onClick = {
                        viewModel.onShowHistoryChanged(false)
                        viewModel.onShowFavoritesChanged(true)
                    }
                )

                Box(modifier = Modifier.weight(1f))

                // 排序
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        when (uiState.sortOption) {
                            SavedLocationsSortOption.RECENT -> stringResource(R.string.sort_recent)
                            SavedLocationsSortOption.NAME_ASC -> stringResource(R.string.sort_name_asc)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(locations, key = { it.id }) { location ->
                    SavedLocationItem(
                        location = location,
                        onClick = { onLocationSelected(location.latitude, location.longitude) },
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

@Composable
fun SavedLocationItem(
    location: SavedLocation,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
