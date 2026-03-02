package com.example.mockgps.ui.routes

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
import com.example.mockgps.data.model.Route
import com.example.mockgps.data.model.RoutePoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    onNavigateBack: () -> Unit,
    onRouteSelected: (Route, List<RoutePoint>) -> Unit,
    viewModel: RoutesViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var routeToDelete by remember { mutableStateOf<Route?>(null) }
    var routeToRename by remember { mutableStateOf<Route?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Routes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(uiState.routes) { route ->
                RouteItem(
                    route = route,
                    onClick = {
                        coroutineScope.launch {
                            val points = viewModel.getRoutePoints(route.id)
                            onRouteSelected(route, points)
                        }
                    },
                    onDeleteClick = { routeToDelete = route },
                    onRenameClick = { routeToRename = route }
                )
                Divider()
            }
        }
    }

    if (routeToDelete != null) {
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text("Delete Route") },
            text = { Text("Are you sure you want to delete '${routeToDelete?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    routeToDelete?.let { viewModel.deleteRoute(it) }
                    routeToDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (routeToRename != null) {
        var newName by remember { mutableStateOf(routeToRename?.name ?: "") }
        AlertDialog(
            onDismissRequest = { routeToRename = null },
            title = { Text("Rename Route") },
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
                        routeToRename?.let { viewModel.renameRoute(it, newName) }
                        routeToRename = null
                    },
                    enabled = newName.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { routeToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RouteItem(
    route: Route,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateString = formatter.format(Date(route.createdAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = route.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Created: $dateString",
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
