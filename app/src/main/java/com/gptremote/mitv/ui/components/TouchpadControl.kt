package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.truncate

/**
 * Full-width responsive Touchpad surface (2-finger drag scrolls on PC / Bluetooth HID, tap = Select/OK).
 */
@Composable
fun TouchpadControl(
    transportMode: TransportMode,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onButtonFallback: (RemoteButton) -> Unit,
    showClickButtons: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isTouching by remember { mutableStateOf(false) }

    val isCursorMode = transportMode == TransportMode.BLUETOOTH_HID ||
        transportMode == TransportMode.WIFI_PC
    val latestIsCursor by rememberUpdatedState(isCursorMode)
    val accent = MiOrange

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = when (transportMode) {
                TransportMode.BLUETOOTH_HID -> "Bluetooth Touchpad"
                TransportMode.WIFI_PC -> "Wi-Fi PC Touchpad"
                TransportMode.WIFI_ANDROID_TV -> "Wi-Fi Swipe Pad"
            },
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showClickButtons) 310.dp else 360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(RemoteSurfaceElevated)
                .border(
                    width = 1.5.dp,
                    color = if (isTouching) accent.copy(alpha = 0.78f) else RemoteBorder,
                    shape = RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    val tapSlopPx = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        firstDown.consume()
                        isTouching = true

                        var maxPointers = 1
                        var movedBeyondTap = false
                        var totalTravel = Offset.Zero
                        var remX = 0f
                        var remY = 0f
                        var remScroll = 0f
                        var swipeAccumX = 0f
                        var swipeAccumY = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.size > maxPointers) {
                                maxPointers = activeChanges.size
                            }

                            if (activeChanges.isEmpty()) {
                                if (!movedBeyondTap) {
                                    onMouseClick(
                                        if (maxPointers >= 2) MouseButton.RIGHT else MouseButton.LEFT
                                    )
                                }
                                isTouching = false
                                break
                            }

                            if (activeChanges.size >= 2) {
                                val avgDeltaY = activeChanges
                                    .map { it.positionChange().y }
                                    .average()
                                    .toFloat()
                                activeChanges.forEach { it.consume() }
                                totalTravel += Offset(0f, abs(avgDeltaY))
                                if (totalTravel.getDistance() > tapSlopPx) {
                                    movedBeyondTap = true
                                }
                                remScroll += avgDeltaY
                                if (latestIsCursor) {
                                    val step = 18f
                                    if (abs(remScroll) >= step) {
                                        val wheelTicks = (remScroll / step).toInt()
                                        remScroll -= wheelTicks * step
                                        onMouseMove(0, 0, -wheelTicks)
                                    }
                                } else {
                                    val step = 50f
                                    if (abs(remScroll) >= step) {
                                        onButtonFallback(
                                            if (remScroll > 0) RemoteButton.DPAD_DOWN else RemoteButton.DPAD_UP
                                        )
                                        remScroll = 0f
                                    }
                                }
                            } else {
                                val primary = activeChanges.first()
                                val delta = primary.positionChange()
                                primary.consume()

                                if (delta != Offset.Zero) {
                                    totalTravel += Offset(abs(delta.x), abs(delta.y))
                                    if (totalTravel.getDistance() > tapSlopPx) {
                                        movedBeyondTap = true
                                    }

                                    if (latestIsCursor) {
                                        val factor = 1.65f
                                        remX += delta.x * factor
                                        remY += delta.y * factor
                                        val sendX = truncate(remX).toInt()
                                        val sendY = truncate(remY).toInt()
                                        if (sendX != 0 || sendY != 0) {
                                            remX -= sendX
                                            remY -= sendY
                                            onMouseMove(sendX, sendY, 0)
                                        }
                                    } else {
                                        swipeAccumX += delta.x
                                        swipeAccumY += delta.y
                                        val threshold = 60f
                                        if (abs(swipeAccumX) > abs(swipeAccumY) && abs(swipeAccumX) >= threshold) {
                                            onButtonFallback(
                                                if (swipeAccumX > 0) RemoteButton.DPAD_RIGHT else RemoteButton.DPAD_LEFT
                                            )
                                            swipeAccumX = 0f
                                            swipeAccumY = 0f
                                        } else if (abs(swipeAccumY) >= threshold) {
                                            onButtonFallback(
                                                if (swipeAccumY > 0) RemoteButton.DPAD_DOWN else RemoteButton.DPAD_UP
                                            )
                                            swipeAccumX = 0f
                                            swipeAccumY = 0f
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isCursorMode) "Drag to move cursor" else "Swipe to navigate",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isCursorMode) {
                        "Tap = Click • 2-finger tap = Right-click • 2-finger slide = Scroll"
                    } else {
                        "Tap = OK • 2-finger tap = Back • 2-finger slide = Scroll"
                    },
                    color = TextSecondary.copy(alpha = 0.72f),
                    fontSize = 11.sp
                )
            }
        }

        if (showClickButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onMouseClick(MouseButton.LEFT) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Left Click")
                }
                OutlinedButton(
                    onClick = { onMouseClick(MouseButton.RIGHT) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Right Click")
                }
            }
        }
    }
}
