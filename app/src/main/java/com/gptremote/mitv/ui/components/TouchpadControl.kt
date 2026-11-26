package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.MouseButton
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TouchpadControl(
    transportMode: TransportMode,
    onMouseMove: (dx: Int, dy: Int, wheel: Int) -> Unit,
    onMouseClick: (MouseButton) -> Unit,
    onButtonFallback: (RemoteButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var sensitivity by remember { mutableFloatStateOf(1.5f) }
    var swipeAccumX by remember { mutableFloatStateOf(0f) }
    var swipeAccumY by remember { mutableFloatStateOf(0f) }
    var scrollAccum by remember { mutableFloatStateOf(0f) }
    var isBluetoothHid by remember(transportMode) {
        mutableStateOf(transportMode == TransportMode.BLUETOOTH_HID)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBluetoothHid) "HID Mouse Touchpad" else "Swipe Pad (Wi-Fi D-Pad)",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (isBluetoothHid) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.0f to "1x", 1.5f to "1.5x", 2.2f to "2.2x").forEach { (factor, label) ->
                        FilterChip(
                            selected = sensitivity == factor,
                            onClick = { sensitivity = factor },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main touchpad surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.5.dp, RemoteBorder, RoundedCornerShape(22.dp))
                    .pointerInput(isBluetoothHid, sensitivity) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMouseClick(MouseButton.LEFT)
                            },
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMouseClick(MouseButton.RIGHT)
                            }
                        )
                    }
                    .pointerInput(isBluetoothHid, sensitivity) {
                        detectDragGestures(
                            onDragEnd = {
                                swipeAccumX = 0f
                                swipeAccumY = 0f
                            },
                            onDragCancel = {
                                swipeAccumX = 0f
                                swipeAccumY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (isBluetoothHid) {
                                    val dx = (dragAmount.x * sensitivity).roundToInt().coerceIn(-127, 127)
                                    val dy = (dragAmount.y * sensitivity).roundToInt().coerceIn(-127, 127)
                                    if (dx != 0 || dy != 0) {
                                        onMouseMove(dx, dy, 0)
                                    }
                                } else {
                                    // Wi-Fi fallback: convert swipes into D-pad moves
                                    swipeAccumX += dragAmount.x
                                    swipeAccumY += dragAmount.y
                                    val threshold = 64f
                                    if (abs(swipeAccumX) > abs(swipeAccumY) && abs(swipeAccumX) >= threshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onButtonFallback(
                                            if (swipeAccumX > 0) RemoteButton.DPAD_RIGHT else RemoteButton.DPAD_LEFT
                                        )
                                        swipeAccumX = 0f
                                        swipeAccumY = 0f
                                    } else if (abs(swipeAccumY) >= threshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onButtonFallback(
                                            if (swipeAccumY > 0) RemoteButton.DPAD_DOWN else RemoteButton.DPAD_UP
                                        )
                                        swipeAccumX = 0f
                                        swipeAccumY = 0f
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isBluetoothHid) "Drag to move cursor" else "Swipe to navigate",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBluetoothHid) "Tap = Click • Double-tap = Back/Right-click" else "Tap = OK • Double-tap = Back",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            // Dedicated vertical scroll strip for Bluetooth HID
            if (isBluetoothHid) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RemoteSurface)
                        .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { scrollAccum = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    scrollAccum += dragAmount.y
                                    val step = 28f
                                    if (abs(scrollAccum) >= step) {
                                        val wheel = if (scrollAccum > 0) -1 else 1
                                        onMouseMove(0, 0, wheel)
                                        scrollAccum = 0f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S\nC\nR\nO\nL\nL",
                        color = MiOrange.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onMouseClick(MouseButton.LEFT) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isBluetoothHid) "Left Click" else "OK / Select")
            }
            OutlinedButton(
                onClick = { onMouseClick(MouseButton.RIGHT) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isBluetoothHid) "Right Click" else "Back")
            }
        }
    }
}
