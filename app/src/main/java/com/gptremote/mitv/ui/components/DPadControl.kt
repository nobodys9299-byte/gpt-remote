package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gptremote.mitv.model.RemoteButton
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DPadControl(
    onButtonPress: (RemoteButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(248.dp)
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(RemoteSurfaceElevated, RemoteSurface)
                )
            )
            .border(1.5.dp, RemoteBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RepeatableDirButton(
                icon = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Up",
                onTrigger = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onButtonPress(RemoteButton.DPAD_UP)
                }
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RepeatableDirButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Left",
                    onTrigger = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onButtonPress(RemoteButton.DPAD_LEFT)
                    }
                )

                CenterOkButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onButtonPress(RemoteButton.DPAD_CENTER)
                    }
                )

                RepeatableDirButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Right",
                    onTrigger = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onButtonPress(RemoteButton.DPAD_RIGHT)
                    }
                )
            }

            RepeatableDirButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Down",
                onTrigger = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onButtonPress(RemoteButton.DPAD_DOWN)
                }
            )
        }
    }
}

@Composable
private fun CenterOkButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(86.dp)
            .clip(CircleShape)
            .background(
                if (pressed) MiOrange else Color(0xFF2B3242)
            )
            .border(2.dp, if (pressed) Color.White.copy(alpha = 0.4f) else MiOrange.copy(alpha = 0.65f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onClick()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "OK",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun RepeatableDirButton(
    icon: ImageVector,
    contentDescription: String,
    onTrigger: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(if (pressed) MiOrange.copy(alpha = 0.28f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onTrigger()
                        val repeatJob = scope.launch {
                            delay(340)
                            while (pressed) {
                                onTrigger()
                                delay(110)
                            }
                        }
                        tryAwaitRelease()
                        pressed = false
                        repeatJob.cancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (pressed) MiOrange else TextPrimary,
            modifier = Modifier.size(36.dp)
        )
    }
}
