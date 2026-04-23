package com.moooo_works.letsgogps.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.ui.theme.Accent500
import kotlinx.coroutines.launch

/** Top search bar that also shows the current mock/simulation status. */
@Composable
fun MapSearchBar(
    centerLocation: LatLng,
    statusText: String,
    statusActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.map_search_location),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${"%.4f".format(centerLocation.latitude)}° N," +
                            "  ${"%.4f".format(centerLocation.longitude)}° E",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (statusActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (statusActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** FAB that jumps the camera to the device's last known location. */
@Composable
fun MyLocationFab(
    cameraPositionState: CameraPositionState,
    onLocationFound: (LatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(end = 16.dp, top = 60.dp)
    ) {
        SmallFloatingActionButton(
            onClick = {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val target = LatLng(loc.latitude, loc.longitude)
                        coroutineScope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(target, 15f)
                            )
                            onLocationFound(target)
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.map_my_location))
        }
    }
}

/** Column of small FABs at the bottom-end of the map: map-type toggle + joystick toggle. */
@Composable
fun MapActionFabs(
    uiState: MapUiState,
    onToggleMapType: () -> Unit,
    onToggleJoystick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        SmallFloatingActionButton(
            onClick = onToggleMapType,
            containerColor = if (uiState.mapType == com.google.maps.android.compose.MapType.HYBRID)
                Accent500 else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (uiState.mapType == com.google.maps.android.compose.MapType.HYBRID)
                Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.map_layer_toggle))
        }

        SmallFloatingActionButton(
            onClick = onToggleJoystick,
            containerColor = if (uiState.isJoystickEnabled) Accent500 else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (uiState.isJoystickEnabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(
                if (uiState.isProActive) Icons.Default.ControlCamera else Icons.Default.Lock,
                contentDescription = stringResource(R.string.map_joystick_toggle)
            )
        }
    }
}

/** Toolbar that appears when the user taps a saved location marker. */
@Composable
fun SelectedLocationActionBar(
    selectedLocation: SavedLocation,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 60.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (selectedLocation.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (selectedLocation.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
