package com.example.mockgps.ui.savedlocations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mockgps.data.model.SavedLocation

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("儲存位置") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜尋名稱") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "排序")
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        when (uiState.sortOption) {
                            SavedLocationsSortOption.RECENT -> "最近新增"
                            SavedLocationsSortOption.NAME_ASC -> "名稱 A→Z"
                        }
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("最近新增") },
                        onClick = {
                            viewModel.onSortOptionChanged(SavedLocationsSortOption.RECENT)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("名稱 A→Z") },
                        onClick = {
                            viewModel.onSortOptionChanged(SavedLocationsSortOption.NAME_ASC)
                            sortMenuExpanded = false
                        }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.showHistory,
                    onCheckedChange = viewModel::onShowHistoryChanged
                )
                Text("History")

                Checkbox(
                    checked = uiState.showFavorites,
                    onCheckedChange = viewModel::onShowFavoritesChanged
                )
                Text("Favorites")
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(locations, key = { it.id }) { location ->
                    SavedLocationItem(
                        location = location,
                        onClick = { onLocationSelected(location.latitude, location.longitude) },
                        onFavoriteClick = { viewModel.toggleFavorite(location) },
                        onDeleteClick = { locationToDelete = location },
                        onRenameClick = { locationToRename = location }
                    )
                    Divider()
                }
            }
        }
    }

    if (locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { locationToDelete = null },
            title = { Text("Delete Location") },
            text = { Text("Are you sure you want to delete '${locationToDelete?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    locationToDelete?.let { viewModel.deleteLocation(it) }
                    locationToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (locationToRename != null) {
        var newName by remember(locationToRename?.id) { mutableStateOf(locationToRename?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { locationToRename = null },
            title = { Text("Rename Location") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 40) newName = it },
                    label = { Text("Name") },
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
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { locationToRename = null }) {
                    Text("Cancel")
                }
            }
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
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = location.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (location.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (location.isFavorite) "Unfavorite" else "Favorite"
            )
        }
        IconButton(onClick = onRenameClick) {
            Icon(Icons.Default.Edit, contentDescription = "Rename")
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
