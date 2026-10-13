package com.moooo_works.letsgogps.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moooo_works.letsgogps.R
import java.util.Locale
import kotlin.math.*

@Composable
fun JoystickOverlayView(
    transportMode: TransportMode,
    speedKmh: Double,
    onMove: (deltaX: Float, deltaY: Float) -> Unit,
    onWindowDrag: (deltaX: Int, deltaY: Int) -> Unit,
    onWindowDragEnd: () -> Unit = {},
    onTransportModeChange: (TransportMode) -> Unit,
    onStop: () -> Unit
) {
    DisposableEffect(Unit) { onDispose { onMove(0f, 0f) } }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val currentMove by rememberUpdatedState(onMove)
    val currentWindowDrag by rememberUpdatedState(onWindowDrag)
    val currentWindowDragEnd by rememberUpdatedState(onWindowDragEnd)
    val radius = 100f
    val colors = MaterialTheme.colorScheme
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val modes = TransportMode.entries
    val modeIndex = modes.indexOf(transportMode)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(120.dp).background(colors.surfaceVariant, CircleShape).border(1.dp, colors.outline, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { currentWindowDragEnd() }, onDragCancel = { currentWindowDragEnd() }) { change, delta ->
                            change.consume(); currentWindowDrag(delta.x.roundToInt(), delta.y.roundToInt())
                        }
                    })
                Box(Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.size(60.dp)
                    .background(colors.secondaryContainer, CircleShape).border(2.dp, colors.primary, CircleShape)
                    .pointerInput(Unit) {
                        fun release() { offsetX = 0f; offsetY = 0f; currentMove(0f, 0f) }
                        detectDragGestures(onDragEnd = { release() }, onDragCancel = { release() }) { change, delta ->
                            change.consume()
                            val x = offsetX + delta.x; val y = offsetY + delta.y
                            val distance = sqrt(x * x + y * y)
                            if (distance <= radius) { offsetX = x; offsetY = y }
                            else { val angle = atan2(y, x); offsetX = cos(angle) * radius; offsetY = sin(angle) * radius }
                            currentMove(offsetX / radius, offsetY / radius)
                        }
                    })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.floating_speed, speedKmh), fontSize = 12.sp, color = colors.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceVariant) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onTransportModeChange(modes[modeIndex - 1]) }, enabled = modeIndex > 0) { Icon(Icons.Default.Remove, stringResource(R.string.floating_slower), Modifier.size(18.dp)) }
                        Text(String.format(locale, "%.1f", speedKmh), Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        IconButton(onClick = { onTransportModeChange(modes[modeIndex + 1]) }, enabled = modeIndex < modes.lastIndex) { Icon(Icons.Default.Add, stringResource(R.string.floating_faster), Modifier.size(18.dp)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEach { mode ->
                        val label = stringResource(when(mode) { TransportMode.WALKING -> R.string.map_transport_walking; TransportMode.CYCLING -> R.string.map_transport_cycling; TransportMode.DRIVING -> R.string.map_transport_driving })
                        OutlinedButton(onClick = { onTransportModeChange(mode) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (mode == transportMode) colors.secondaryContainer else colors.surfaceVariant,
                                contentColor = if (mode == transportMode) colors.primary else colors.onSurfaceVariant),
                            border = BorderStroke(1.dp, if (mode == transportMode) colors.primary else colors.outlineVariant)) {
                            Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { onMove(0f, 0f); onStop() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.error), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)) {
            Icon(Icons.Default.Stop, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_stop), fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
internal fun StandaloneJoystickOverlay(
    onClose: () -> Unit,
    onCollapse: () -> Unit,
    onWindowDrag: (Int, Int) -> Unit,
    onWindowDragEnd: () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val dragWindow by rememberUpdatedState(onWindowDrag)
    val finishDrag by rememberUpdatedState(onWindowDragEnd)
    val drag = Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = { finishDrag() }, onDragCancel = { finishDrag() }) { change, delta ->
            change.consume(); dragWindow(delta.x.roundToInt(), delta.y.roundToInt())
        }
    }
    val colors = MaterialTheme.colorScheme
    if (!expanded) {
        Surface(color = colors.surface.copy(alpha = .90f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.5.dp, colors.primary)) {
            Box(Modifier.size(48.dp).then(drag).clickable { expanded = true; onCollapse() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ControlCamera, stringResource(R.string.joystick_title), tint = colors.primary)
            }
        }
        return
    }
    val config = LocalConfiguration.current
    Surface(modifier = Modifier.width(minOf(358, config.screenWidthDp - 32).coerceAtLeast(280).dp),
        color = colors.surface.copy(alpha = .82f), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DragIndicator, null, Modifier.size(24.dp).then(drag), tint = colors.onSurfaceVariant)
                Text(stringResource(R.string.joystick_title), Modifier.weight(1f).then(drag), fontWeight = FontWeight.Bold)
                IconButton(onClick = { expanded = false; onCollapse() }) {
                    Icon(Icons.Default.CloseFullscreen, stringResource(R.string.joystick_collapse))
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.joystick_close)) }
            }
            content()
        }
    }
}
