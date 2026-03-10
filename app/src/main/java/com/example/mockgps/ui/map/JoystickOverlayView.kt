package com.example.mockgps.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
fun JoystickOverlayView(
    transportMode: TransportMode,
    onMove: (deltaX: Float, deltaY: Float) -> Unit,
    onWindowDrag: (deltaX: Int, deltaY: Int) -> Unit,
    onToggleSpeed: () -> Unit,
    onStop: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isExpanded by remember { mutableStateOf(false) }
    val radius = 100f // Base radius in pixels for the joystick

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(12.dp)
    ) {
        // 1. Controls Capsule (Expanded State)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = Color(0xE61F2937),
                shape = RoundedCornerShape(30.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.width(200.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Toggle Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF374151))
                            .clickable { onToggleSpeed() },
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (transportMode) {
                            TransportMode.WALKING -> "🚶"
                            TransportMode.CYCLING -> "🚲"
                            TransportMode.DRIVING -> "🚗"
                        }
                        Text(icon, fontSize = 18.sp)
                    }

                    // Stop Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .clickable { onStop() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏹", color = Color.White, fontSize = 18.sp)
                    }

                    // Collapse Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF374151))
                            .clickable { isExpanded = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "收合",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Joystick Base & Handle
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Settings Button (Positioned outside/on edge of the base)
            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (8).dp, y = (-8).dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xE61F2937))
                        .clickable { isExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "選單",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Base Disk
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xE61F2937))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onWindowDrag(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                        }
                    }
            )

            // Move Handle
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5722))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                offsetX = 0f
                                offsetY = 0f
                                onMove(0f, 0f)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                
                                val newX = offsetX + dragAmount.x
                                val newY = offsetY + dragAmount.y
                                val distance = sqrt(newX * newX + newY * newY)
                                
                                if (distance <= radius) {
                                    offsetX = newX
                                    offsetY = newY
                                } else {
                                    val angle = atan2(newY, newX)
                                    offsetX = cos(angle) * radius
                                    offsetY = sin(angle) * radius
                                }
                                
                                onMove(offsetX / radius, offsetY / radius)
                            }
                        )
                    }
            )
        }
    }
}
