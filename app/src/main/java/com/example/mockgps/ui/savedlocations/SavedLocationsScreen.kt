package com.example.mockgps.ui.savedlocations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mockgps.data.model.SavedLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLocationsScreen(
    onNavigateBack: () -> Unit,
    onLocationSelected: (Double, Double) -> Unit,
    viewModel: SavedLocationsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var locationToDelete by remember { mutableStateOf<SavedLocation?>(null) }
    var locationToRename by remember { mutableStateOf<SavedLocation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("儲存位置") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(uiState.locations) { location ->
                SavedLocationItem(
                    location = location,
                    onClick = { onLocationSelected(location.latitude, location.longitude) },
                    onDeleteClick = { locationToDelete = location },
                    onRenameClick = { locationToRename = location }
                )
                Divider()
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text = location.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Lat: %.6f, Lng: %.6f".format(location.latitude, location.longitude),
                style = MaterialTheme.typography.bodySmall
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
