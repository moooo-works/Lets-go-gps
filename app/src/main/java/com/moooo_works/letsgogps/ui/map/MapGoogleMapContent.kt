package com.moooo_works.letsgogps.ui.map

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.SimulationState
import kotlinx.coroutines.launch

/**
 * Renders the Google Map layer including saved location markers,
 * waypoint markers, the current mock location marker, and the route polyline.
 * Also computes waypoint icons internally to keep icon creation co-located
 * with the composable that uses them.
 */
@Composable
fun MapGoogleMapContent(
    uiState: MapUiState,
    cameraPositionState: CameraPositionState,
    mockMarkerState: MarkerState,
    onSingleModeMapClick: (LatLng) -> Unit,
    onRouteMapClick: (LatLng) -> Unit,
    onWaypointClick: (Int) -> Unit,
    onSavedLocationClick: (SavedLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val waypointIcons = remember(uiState.waypoints.size, primaryColorArgb) {
        uiState.waypoints.mapIndexed { index, _ ->
            val label = when (index) {
                0 -> "S"
                uiState.waypoints.size - 1 -> "E"
                else -> "${index + 1}"
            }
            createNumberedMarkerBitmap(label, primaryColorArgb)
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, rotationGesturesEnabled = false),
        properties = MapProperties(isMyLocationEnabled = false, mapType = uiState.mapType),
        onMapClick = { latLng ->
            when (uiState.mapMode) {
                MapMode.SINGLE -> coroutineScope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
                    onSingleModeMapClick(latLng)
                }
                MapMode.ROUTE -> {
                    val isSimulating = uiState.simulationState == SimulationState.PLAYING
                            || uiState.simulationState == SimulationState.PAUSED
                    if (!isSimulating) onRouteMapClick(latLng)
                }
            }
        }
    ) {
        // Saved location markers
        uiState.savedLocations.forEach { location ->
            Marker(
                state = MarkerState(position = LatLng(location.latitude, location.longitude)),
                title = location.name,
                snippet = location.description.takeIf { it.isNotBlank() },
                onClick = {
                    onSavedLocationClick(location)
                    false
                },
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (location.isFavorite) BitmapDescriptorFactory.HUE_RED
                    else BitmapDescriptorFactory.HUE_ORANGE
                )
            )
        }

        // Waypoint markers
        uiState.waypoints.forEachIndexed { index, point ->
            val isSimulating = uiState.simulationState == SimulationState.PLAYING
                    || uiState.simulationState == SimulationState.PAUSED
            Marker(
                state = MarkerState(position = point),
                title = "路點 ${index + 1}",
                icon = waypointIcons.getOrNull(index) ?: BitmapDescriptorFactory.defaultMarker(),
                onClick = {
                    if (!isSimulating) onWaypointClick(index)
                    true
                }
            )
        }

        // Current mock location marker
        if (uiState.currentMockLocation != null) {
            Marker(
                state = mockMarkerState,
                title = "Current Mock Location",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }

        // Route polyline
        if (uiState.waypoints.size > 1) {
            Polyline(points = uiState.waypoints, color = Color.Blue, width = 10f)
        }
    }
}

/** Red crosshair overlay drawn over the centre of the map container. */
@Composable
fun MapCrosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val arm = size.width
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawRect(
            color = Color.Red,
            topLeft = Offset(0f, cy - stroke / 2f),
            size = Size(arm, stroke)
        )
        drawRect(
            color = Color.Red,
            topLeft = Offset(cx - stroke / 2f, 0f),
            size = Size(stroke, arm)
        )
    }
}

/** Creates a circular bitmap descriptor with a centred label for waypoint markers. */
internal fun createNumberedMarkerBitmap(
    label: String,
    colorArgb: Int
): com.google.android.gms.maps.model.BitmapDescriptor {
    val sizePx = 80
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (label.length > 1) 24f else 30f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, sizePx / 2f, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
