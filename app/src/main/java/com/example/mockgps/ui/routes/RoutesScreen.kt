package com.example.mockgps.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: RoutesViewModel,
    onNavigateBack: () -> Unit,
    onRouteSelected: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var routePendingDelete by remember { mutableStateOf<Int?>(null) }
    var routePendingRename by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Routes") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            items(uiState.routes, key = { it.id }) { route ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onRouteSelected(route.id) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(route.name)
                        Text("Points: ${route.pointCount}")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { routePendingRename = route.id }) { Text("Rename") }
                            TextButton(onClick = { routePendingDelete = route.id }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    if (routePendingRename != null) {
        val routeId = routePendingRename!!
        val initialName = uiState.routes.firstOrNull { it.id == routeId }?.name.orEmpty()
        var renameInput by rememberSaveable(routeId) { mutableStateOf(initialName) }
        AlertDialog(
            onDismissRequest = { routePendingRename = null },
            title = { Text("Rename Route") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    supportingText = { Text("1-40 chars") }
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
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { routePendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (routePendingDelete != null) {
        val routeId = routePendingDelete!!
        AlertDialog(
            onDismissRequest = { routePendingDelete = null },
            title = { Text("Delete Route") },
            text = { Text("Are you sure you want to delete this route?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteRoute(routeId)
                    routePendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { routePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
