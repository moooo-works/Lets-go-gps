package com.example.mockgps.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.*
@Composable
fun JoystickOverlayView(
    onMove: (deltaX: Float, deltaY: Float) -> Unit,
    onWindowDrag: (deltaX: Int, deltaY: Int) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val radius = 100f // Base radius in pixels

    Box(
        modifier = Modifier
            .size(150.dp)
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                }
            },
        contentAlignment = Alignment.Center
    ) {
...

        // Joystick Base
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Joystick Handle
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = 0.8f)) // App primary green
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
                            
                            // Normalize to -1.0 to 1.0
                            onMove(offsetX / radius, offsetY / radius)
                        }
                    )
                }
        )
    }
}
