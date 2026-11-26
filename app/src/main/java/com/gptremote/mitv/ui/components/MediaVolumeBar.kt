package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import com.gptremote.mitv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaVolumeBar(
    onButtonPress: (RemoteButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Volume + Mute + Channel rockers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volume Rocker
            RockerPill(
                label = "VOL",
                upIcon = Icons.Rounded.Add,
                downIcon = Icons.Rounded.Remove,
                onUp = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onButtonPress(RemoteButton.VOLUME_UP)
                },
                onDown = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onButtonPress(RemoteButton.VOLUME_DOWN)
                },
                modifier = Modifier.weight(1f)
            )

            // Center Mute button
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onButtonPress(RemoteButton.MUTE)
                },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = RemoteSurfaceElevated,
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = "Mute"
                )
            }

            // Channel Rocker
            RockerPill(
                label = "CH",
                upIcon = Icons.Rounded.KeyboardArrowUp,
                downIcon = Icons.Rounded.KeyboardArrowDown,
                onUp = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onButtonPress(RemoteButton.CHANNEL_UP)
                },
                onDown = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onButtonPress(RemoteButton.CHANNEL_DOWN)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Media Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(RemoteSurface)
                .border(1.dp, RemoteBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaButton(
                icon = Icons.Rounded.SkipPrevious,
                description = "Previous",
                onClick = { onButtonPress(RemoteButton.MEDIA_PREVIOUS) }
            )
            MediaButton(
                icon = Icons.Rounded.PlayArrow,
                description = "Play / Pause",
                tint = MiOrange,
                onClick = { onButtonPress(RemoteButton.MEDIA_PLAY_PAUSE) }
            )
            MediaButton(
                icon = Icons.Rounded.Stop,
                description = "Stop",
                onClick = { onButtonPress(RemoteButton.MEDIA_STOP) }
            )
            MediaButton(
                icon = Icons.Rounded.SkipNext,
                description = "Next",
                onClick = { onButtonPress(RemoteButton.MEDIA_NEXT) }
            )
        }
    }
}

@Composable
private fun RockerPill(
    label: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(27.dp))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RepeatableSmallButton(icon = downIcon, description = "$label Down", onTrigger = onDown)
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        RepeatableSmallButton(icon = upIcon, description = "$label Up", onTrigger = onUp)
    }
}

@Composable
private fun RepeatableSmallButton(
    icon: ImageVector,
    description: String,
    onTrigger: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (pressed) MiOrange.copy(alpha = 0.25f) else RemoteSurface.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onTrigger()
                        val job = scope.launch {
                            delay(340)
                            while (pressed) {
                                onTrigger()
                                delay(120)
                            }
                        }
                        tryAwaitRelease()
                        pressed = false
                        job.cancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (pressed) MiOrange else TextPrimary
        )
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color = TextPrimary,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    FilledTonalIconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = RemoteSurfaceElevated,
            contentColor = tint
        )
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}
