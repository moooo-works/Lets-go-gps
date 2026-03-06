package com.example.mockgps.ui.savedlocations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mockgps.data.model.SavedLocation

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "儲存位置",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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
                placeholder = { Text("搜尋儲存位置...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    label = "全部",
                    selected = uiState.showHistory && uiState.showFavorites,
                    onClick = {
                        viewModel.onShowHistoryChanged(true)
                        viewModel.onShowFavoritesChanged(true)
                    }
                )
                // 我的最愛 pill
                FilterPill(
                    label = "我的最愛",
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
                            SavedLocationsSortOption.RECENT -> "最近新增"
                            SavedLocationsSortOption.NAME_ASC -> "名稱 A→Z"
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

    if (locationToDelete != null) {
        AlertDialog(
            onDismissRequest = { locationToDelete = null },
            title = { Text("刪除位置") },
            text = { Text("確定要刪除「${locationToDelete?.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    locationToDelete?.let { viewModel.deleteLocation(it) }
                    locationToDelete = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { locationToDelete = null }) { Text("取消") }
            }
        )
    }

    if (locationToRename != null) {
        var newName by remember(locationToRename?.id) { mutableStateOf(locationToRename?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { locationToRename = null },
            title = { Text("重新命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 40) newName = it },
                    label = { Text("名稱") },
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
                ) { Text("儲存") }
            },
            dismissButton = {
                TextButton(onClick = { locationToRename = null }) { Text("取消") }
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
            .height(72.dp)
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
            Text(
                text = "%.4f° N, %.4f° E".format(location.latitude, location.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (location.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (location.isFavorite) "取消收藏" else "加入收藏",
                tint = if (location.isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRenameClick) {
            Icon(Icons.Default.Edit, contentDescription = "重新命名", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
