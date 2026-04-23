package com.moooo_works.letsgogps.ui.map

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.utils.LocationQueryParser
import com.moooo_works.letsgogps.utils.ParseResult
import kotlinx.coroutines.delay

// ─── Save Route ─────────────────────────────────────────────────────────────

@Composable
fun SaveRouteDialog(
    routeNameInput: String,
    onNameChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val normalized = routeNameInput.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.route_save_title)) },
        text = {
            OutlinedTextField(
                value = routeNameInput,
                onValueChange = onNameChange,
                singleLine = true,
                supportingText = { Text("1-40 chars") }
            )
        },
        confirmButton = {
            Button(
                enabled = normalized.isNotEmpty() && normalized.length <= 40,
                onClick = { onConfirm(normalized) }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

// ─── Mock Error ──────────────────────────────────────────────────────────────

/**
 * Displays a contextual error dialog for mock-location failures.
 * Handles permission routing for NotMockAppSelected, FloatingWindow,
 * LocationPermission, and NotificationPermission error types.
 */
@Composable
fun MockErrorDialog(
    error: MockError,
    onClearError: () -> Unit,
    onRequestPermissions: (Array<String>) -> Unit
) {
    val context = LocalContext.current
    var isButtonEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        isButtonEnabled = false
        delay(500)
        isButtonEnabled = true
    }

    AlertDialog(
        onDismissRequest = onClearError,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            Text(
                if (error is MockError.NotMockAppSelected ||
                    error is MockError.LocationPermissionMissing ||
                    error is MockError.NotificationPermissionMissing ||
                    error is MockError.FloatingWindowPermissionMissing
                ) stringResource(R.string.error_permission_required)
                else stringResource(R.string.error_title)
            )
        },
        text = {
            Column {
                Text(
                    text = when (error) {
                        is MockError.NotMockAppSelected -> stringResource(R.string.error_mock_app_not_selected)
                        is MockError.LocationPermissionMissing -> stringResource(R.string.error_location_permission)
                        is MockError.NotificationPermissionMissing -> stringResource(R.string.error_notification_permission)
                        is MockError.FloatingWindowPermissionMissing -> stringResource(R.string.error_floating_window)
                        is MockError.ProviderSetupFailed -> stringResource(R.string.error_provider_setup_failed, error.message)
                        is MockError.SetLocationFailed -> stringResource(R.string.error_set_location_failed, error.message)
                        is MockError.ProviderTeardownFailed -> stringResource(R.string.error_provider_teardown_failed, error.message)
                        is MockError.InvalidInput -> stringResource(R.string.error_invalid_input, error.message)
                        is MockError.PermissionCheckFailed -> stringResource(R.string.error_permission_check_failed, error.message)
                        is MockError.Unknown -> stringResource(R.string.error_unknown, error.message)
                    }
                )
            }
        },
        confirmButton = {
            when {
                error is MockError.NotMockAppSelected -> {
                    Button(
                        onClick = {
                            onClearError()
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent("android.settings.DEVELOPMENT_SETTINGS"))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        enabled = isButtonEnabled
                    ) {
                        Text(
                            if (isButtonEnabled) stringResource(R.string.action_go_to_settings)
                            else stringResource(R.string.action_please_wait)
                        )
                    }
                }
                error is MockError.FloatingWindowPermissionMissing -> {
                    Button(
                        onClick = {
                            onClearError()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.action_go_to_enable))
                    }
                }
                error is MockError.LocationPermissionMissing -> {
                    Button(
                        onClick = {
                            onClearError()
                            onRequestPermissions(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.action_grant_location))
                    }
                }
                error is MockError.NotificationPermissionMissing -> {
                    Button(
                        onClick = {
                            onClearError()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                onRequestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_grant_notification))
                    }
                }
                else -> {
                    TextButton(onClick = onClearError) { Text(stringResource(R.string.action_ok)) }
                }
            }
        },
        dismissButton = {
            if (error is MockError.NotMockAppSelected) {
                TextButton(onClick = onClearError) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

// ─── Search ──────────────────────────────────────────────────────────────────

@Composable
fun SearchDialog(
    searchState: SearchUiState,
    centerLocation: LatLng,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectResult: (GeocodedLocation) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val searchLatLngLocationTitle = stringResource(R.string.search_latlng_location)

    val performAction = { query: String ->
        val parseResult = LocationQueryParser.parse(query, centerLocation)
        if (parseResult is ParseResult.Success) {
            onSelectResult(
                GeocodedLocation(
                    name = searchLatLngLocationTitle,
                    address = query,
                    latLng = parseResult.parsedLocation.latLng
                )
            )
        } else {
            onSearch(query)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp, vertical = 40.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.search_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        performAction(searchQuery)
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                // Content area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        searchState.isSearching -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        searchState.searchError != null -> {
                            Text(
                                text = stringResource(R.string.search_failed, searchState.searchError),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        searchState.searchResults.isEmpty() && searchQuery.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.search_hint_start),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        searchState.searchResults.isEmpty() && searchQuery.isNotEmpty() && !searchState.isSearching -> {
                            Text(
                                stringResource(R.string.search_no_results),
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(searchState.searchResults) { result ->
                                    SearchResultItem(result = result, onClick = { onSelectResult(result) })
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
fun SearchResultItem(result: GeocodedLocation, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${"%.5f".format(result.latLng.latitude)}, ${"%.5f".format(result.latLng.longitude)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ─── Edit Location ───────────────────────────────────────────────────────────

@Composable
internal fun EditLocationDialog(
    location: SavedLocation,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember(location.id) { mutableStateOf(location.name) }
    var description by remember(location.id) { mutableStateOf(location.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.location_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.location_edit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.location_edit_desc)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
