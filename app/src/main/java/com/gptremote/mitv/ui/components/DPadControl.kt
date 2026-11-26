package com.gptremote.mitv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class DPadDirection {
    NONE, UP, DOWN, LEFT, RIGHT, CENTER
}

/**
 * Gesture-driven circular D-Pad:
 * - Slide anywhere Up / Down / Left / Right to navigate smoothly (repeats continuously while dragging)
 * - Tap anywhere inside the circle to send OK (DPAD_CENTER)
 */
@Composable
fun DPadControl(
    onButtonPress: (RemoteButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnPress by rememberUpdatedState(onButtonPress)
    var activeDir by remember { mutableStateOf(DPadDirection.NONE) }

    LaunchedEffect(activeDir) {
        if (activeDir == DPadDirection.CENTER) {
            delay(150)
            activeDir = DPadDirection.NONE
        }
    }

    val accent = MiOrange
    val ringColor by animateColorAsState(
        targetValue = if (activeDir != DPadDirection.NONE) accent.copy(alpha = 0.85f) else RemoteBorder,
        label = "dpadRing"
    )

    Box(
        modifier = modifier
            .size(256.dp)
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(RemoteSurfaceElevated, RemoteSurface)
                )
            )
            .border(2.dp, ringColor, CircleShape)
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val stepPx = 56f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    var dragAccumX = 0f
                    var dragAccumY = 0f
                    var totalTravel = 0f
                    var didSlide = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change == null) {
                            if (!didSlide && totalTravel <= slop * 1.4f) {
                                activeDir = DPadDirection.CENTER
                                latestOnPress(RemoteButton.DPAD_CENTER)
                            } else {
                                activeDir = DPadDirection.NONE
                            }
                            break
                        }

                        val delta = change.positionChange()
                        change.consume()
                        if (delta == Offset.Zero) continue

                        totalTravel += delta.getDistance()
                        dragAccumX += delta.x
                        dragAccumY += delta.y

                        if (abs(dragAccumX) >= stepPx || abs(dragAccumY) >= stepPx) {
                            didSlide = true
                            if (abs(dragAccumX) > abs(dragAccumY)) {
                                if (dragAccumX > 0f) {
                                    activeDir = DPadDirection.RIGHT
                                    latestOnPress(RemoteButton.DPAD_RIGHT)
                                } else {
                                    activeDir = DPadDirection.LEFT
                                    latestOnPress(RemoteButton.DPAD_LEFT)
                                }
                            } else {
                                if (dragAccumY > 0f) {
                                    activeDir = DPadDirection.DOWN
                                    latestOnPress(RemoteButton.DPAD_DOWN)
                                } else {
                                    activeDir = DPadDirection.UP
                                    latestOnPress(RemoteButton.DPAD_UP)
                                }
                            }
                            dragAccumX = 0f
                            dragAccumY = 0f
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Slide Up",
                tint = if (activeDir == DPadDirection.UP) accent else TextPrimary.copy(alpha = 0.78f),
                modifier = Modifier.size(34.dp)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Slide Left",
                    tint = if (activeDir == DPadDirection.LEFT) accent else TextPrimary.copy(alpha = 0.78f),
                    modifier = Modifier.size(34.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeDir == DPadDirection.CENTER) accent else accent.copy(alpha = 0.22f)
                            )
                            .border(2.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OK",
                            color = if (activeDir == DPadDirection.CENTER) Color.White else TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Slide • Tap OK",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Slide Right",
                    tint = if (activeDir == DPadDirection.RIGHT) accent else TextPrimary.copy(alpha = 0.78f),
                    modifier = Modifier.size(34.dp)
                )
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Slide Down",
                tint = if (activeDir == DPadDirection.DOWN) accent else TextPrimary.copy(alpha = 0.78f),
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
