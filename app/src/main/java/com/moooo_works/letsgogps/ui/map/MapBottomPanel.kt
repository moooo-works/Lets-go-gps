package com.moooo_works.letsgogps.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moooo_works.letsgogps.R
import kotlin.math.roundToInt
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RoutePlaybackMode
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.ui.theme.Accent500

/**
 * Bottom control panel containing the mode selector tabs, the primary
 * action button (start/stop mock / play/pause route), the loop-mode
 * cycle button (NONE → LOOP → BOUNCE), an animated progress bar when
 * a route is active, and the route-specific controls.
 */
@Composable
fun MapBottomPanel(
    uiState: MapUiState,
    onSetMode: (MapMode) -> Unit,
    onShowProUpgrade: () -> Unit,
    onAddWaypoint: () -> Unit,
    onStartMocking: () -> Unit,
    onStopMocking: () -> Unit,
    onPlayRoute: () -> Unit,
    onPauseRoute: () -> Unit,
    onSetSpeed: (Double) -> Unit,
    onSetTransportMode: (TransportMode) -> Unit,
    onTogglePlaybackMode: () -> Unit,
    onSetJumpInterval: (Int) -> Unit,
    onShowSaveRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onCycleLoopMode: () -> Unit,
    onStartExploration: () -> Unit,
    onStartTeleportExploration: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 250)),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode selector tabs
            MapModeSelector(
                uiState = uiState,
                onSetMode = onSetMode,
                onShowProUpgrade = onShowProUpgrade
            )

            // Primary action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isHoldingCompletedRoute = uiState.mapMode == MapMode.ROUTE &&
                    uiState.isMocking &&
                    uiState.simulationState == SimulationState.IDLE
                if (uiState.mapMode == MapMode.ROUTE) {
                    OutlinedButton(
                        onClick = onAddWaypoint,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(stringResource(R.string.route_add_waypoint)) }
                }

                Button(
                    onClick = {
                        if (uiState.mapMode == MapMode.ROUTE) {
                            if (isHoldingCompletedRoute) onStopMocking()
                            else if (uiState.simulationState == SimulationState.PLAYING) onPauseRoute()
                            else if (uiState.waypoints.isNotEmpty()) onPlayRoute()
                        } else {
                            if (uiState.isMocking) {
                                onStopMocking()
                            } else {
                                onStartMocking()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !uiState.isStartingMocking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isMocking || uiState.simulationState == SimulationState.PLAYING)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isStartingMocking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(stringResource(R.string.map_mock_starting))
                        }
                    } else {
                        Text(
                            when {
                                uiState.mapMode == MapMode.ROUTE && uiState.simulationState == SimulationState.PLAYING ->
                                    "⏸ ${stringResource(R.string.map_route_pause)}"
                                isHoldingCompletedRoute ->
                                    "⏹ ${stringResource(R.string.map_mock_stop)}"
                                uiState.mapMode == MapMode.ROUTE && uiState.waypoints.isNotEmpty() ->
                                    "▶ ${stringResource(R.string.map_route_start)}"
                                uiState.mapMode == MapMode.SINGLE && uiState.isMocking ->
                                    "⏹ ${stringResource(R.string.map_mock_stop)}"
                                else -> "▶ ${stringResource(R.string.map_mock_start)}"
                            }
                        )
                    }
                }

                // Loop/Bounce cycle button — only shown in ROUTE mode with ≥ 2 waypoints.
                // Animated so adding the 2nd waypoint (or clearing the route) doesn't
                // snap the primary button's width in a single frame.
                AnimatedVisibility(
                    visible = uiState.mapMode == MapMode.ROUTE && uiState.waypoints.size >= 2,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    LoopModeButton(
                        loopMode = uiState.loopMode,
                        onClick = onCycleLoopMode
                    )
                }

                // Advanced modes: spiral exploration (SINGLE) and teleport-spiral
                // (ROUTE with waypoints). Hidden when neither would activate.
                //
                // Wrapped in AnimatedVisibility so the start/stop transition
                // doesn't snap the main Start button wider in a single frame
                // when this IconButton disappears.
                val showAdvancedButton = (uiState.mapMode == MapMode.SINGLE && !uiState.isMocking) ||
                    (uiState.mapMode == MapMode.ROUTE && uiState.waypoints.size >= 2 &&
                        uiState.simulationState != SimulationState.PLAYING)
                AnimatedVisibility(
                    visible = showAdvancedButton,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    Box {
                        IconButton(onClick = { showAdvancedMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.map_advanced_modes)
                            )
                        }
                        DropdownMenu(
                            expanded = showAdvancedMenu,
                            onDismissRequest = { showAdvancedMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            if (uiState.mapMode == MapMode.SINGLE) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.map_action_spiral_exploration)) },
                                    onClick = {
                                        showAdvancedMenu = false
                                        onStartExploration()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.map_action_teleport_exploration)) },
                                    onClick = {
                                        showAdvancedMenu = false
                                        onStartTeleportExploration()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Route-only controls
            if (uiState.mapMode == MapMode.ROUTE) {
                RouteControls(
                    uiState = uiState,
                    onSetSpeed = onSetSpeed,
                    onSetTransportMode = onSetTransportMode,
                    onTogglePlaybackMode = onTogglePlaybackMode,
                    onSetJumpInterval = onSetJumpInterval,
                    onShowSaveRoute = onShowSaveRoute,
                    onClearRoute = onClearRoute
                )
            }
        }
    }
}

// ─── Loop mode cycle button ───────────────────────────────────────────────────

@Composable
private fun LoopModeButton(loopMode: LoopMode, onClick: () -> Unit) {
    val isActive = loopMode != LoopMode.NONE
    val (icon, label) = when (loopMode) {
        LoopMode.NONE   -> Icons.Filled.Repeat  to stringResource(R.string.route_loop_none)
        LoopMode.LOOP   -> Icons.Filled.Repeat  to stringResource(R.string.route_loop_loop)
        LoopMode.BOUNCE -> Icons.Filled.SyncAlt to stringResource(R.string.route_loop_bounce)
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isActive) Accent500 else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.route_loop_cd),
                modifier = Modifier.size(16.dp),
                tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Mode selector ────────────────────────────────────────────────────────────

@Composable
private fun MapModeSelector(
    uiState: MapUiState,
    onSetMode: (MapMode) -> Unit,
    onShowProUpgrade: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            MapMode.SINGLE to stringResource(R.string.map_mode_single),
            MapMode.ROUTE to if (uiState.isProActive) stringResource(R.string.map_mode_route)
            else "🔒 ${stringResource(R.string.map_mode_route)}"
        ).forEach { (mode, label) ->
            val selected = uiState.mapMode == mode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (mode == MapMode.ROUTE && !uiState.isProActive) {
                            onShowProUpgrade()
                        } else {
                            onSetMode(mode)
                        }
                    },
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Route controls ───────────────────────────────────────────────────────────

@Composable
private fun RouteControls(
    uiState: MapUiState,
    onSetSpeed: (Double) -> Unit,
    onSetTransportMode: (TransportMode) -> Unit,
    onTogglePlaybackMode: () -> Unit,
    onSetJumpInterval: (Int) -> Unit,
    onShowSaveRoute: () -> Unit,
    onClearRoute: () -> Unit
) {
    val isJump = uiState.playbackMode == RoutePlaybackMode.JUMP
    // Walk: speed slider. Jump: interval slider. The leading chip toggles the mode.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = isJump,
            onClick = onTogglePlaybackMode,
            label = {
                Text(
                    "⚡ ${stringResource(R.string.route_jump_mode)}",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            // Primary when active — the default secondaryContainer reads as
            // grey/white and users can't tell whether the mode is on.
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Text(
            if (isJump) stringResource(R.string.route_jump_interval, uiState.jumpIntervalSec)
            else "${"%.0f".format(uiState.speedKmh)} km/h",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp)
        )
        if (isJump) {
            Slider(
                value = uiState.jumpIntervalSec.toFloat(),
                onValueChange = { onSetJumpInterval(it.roundToInt()) },
                valueRange = RouteSimulator.MIN_JUMP_INTERVAL_SEC.toFloat()..RouteSimulator.MAX_JUMP_INTERVAL_SEC.toFloat(),
                steps = RouteSimulator.MAX_JUMP_INTERVAL_SEC - RouteSimulator.MIN_JUMP_INTERVAL_SEC - 1,
                modifier = Modifier.weight(1f)
            )
        } else {
            Slider(
                value = uiState.speedKmh.toFloat(),
                onValueChange = { onSetSpeed(it.roundToInt().toDouble()) },
                valueRange = ROUTE_SPEED_MIN_KMH..ROUTE_SPEED_MAX_KMH,
                steps = ROUTE_SPEED_STEPS,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Transport mode chips + save / clear buttons row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Transport presets only set speed — meaningless while jumping.
            if (!isJump) {
                val transportModeLabels = mapOf(
                    TransportMode.WALKING to "🚶 ${stringResource(R.string.map_transport_walking)}",
                    TransportMode.CYCLING to "🚲 ${stringResource(R.string.map_transport_cycling)}",
                    TransportMode.DRIVING to "🚗 ${stringResource(R.string.map_transport_driving)}"
                )
                TransportMode.values().forEach { mode ->
                    FilterChip(
                        selected = uiState.transportMode == mode,
                        onClick = { onSetTransportMode(mode) },
                        label = {
                            Text(
                                transportModeLabels[mode] ?: mode.name,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (uiState.waypoints.size >= 2) {
                TextButton(onClick = onShowSaveRoute) {
                    Text(stringResource(R.string.action_save))
                }
            }
            if (uiState.waypoints.isNotEmpty()) {
                TextButton(
                    onClick = onClearRoute,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.action_clear)) }
            }
        }
    }
}
