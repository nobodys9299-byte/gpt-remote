package com.gptremote.mitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.abs

/**
 * Vertical slide/tap Volume & Channel rockers with Mute in the center, plus optional Media bar.
 */
@Composable
fun MediaVolumeBar(
    onButtonPress: (RemoteButton) -> Unit,
    showChannelRocker: Boolean = true,
    showMediaRow: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalSlideRocker(
                label = "VOL",
                upIcon = Icons.Rounded.Add,
                downIcon = Icons.Rounded.Remove,
                onUp = { onButtonPress(RemoteButton.VOLUME_UP) },
                onDown = { onButtonPress(RemoteButton.VOLUME_DOWN) },
                modifier = if (showChannelRocker) Modifier.weight(1f) else Modifier.width(120.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onButtonPress(RemoteButton.MUTE) },
                    modifier = Modifier.size(62.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = RemoteSurfaceElevated,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = "Mute",
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text("MUTE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (showChannelRocker) {
                VerticalSlideRocker(
                    label = "CH",
                    upIcon = Icons.Rounded.KeyboardArrowUp,
                    downIcon = Icons.Rounded.KeyboardArrowDown,
                    onUp = { onButtonPress(RemoteButton.CHANNEL_UP) },
                    onDown = { onButtonPress(RemoteButton.CHANNEL_DOWN) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showMediaRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RemoteSurface)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaTransportIcon(Icons.Rounded.SkipPrevious, "Previous") {
                    onButtonPress(RemoteButton.MEDIA_PREVIOUS)
                }
                MediaTransportIcon(Icons.Rounded.PlayArrow, "Play / Pause", accent = true) {
                    onButtonPress(RemoteButton.MEDIA_PLAY_PAUSE)
                }
                MediaTransportIcon(Icons.Rounded.Stop, "Stop") {
                    onButtonPress(RemoteButton.MEDIA_STOP)
                }
                MediaTransportIcon(Icons.Rounded.SkipNext, "Next") {
                    onButtonPress(RemoteButton.MEDIA_NEXT)
                }
            }
        }
    }
}

@Composable
private fun VerticalSlideRocker(
    label: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnUp by rememberUpdatedState(onUp)
    val latestOnDown by rememberUpdatedState(onDown)
    var activeHalf by remember { mutableStateOf(0) } // 1 = up, -1 = down, 0 = idle
    var boxHeightPx by remember { mutableStateOf(1) }
    val accent = MiOrange

    Box(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { boxHeightPx = it.height.coerceAtLeast(1) }
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(RemoteSurfaceElevated, RemoteSurface)
                )
            )
            .border(
                width = 1.5.dp,
                color = if (activeHalf != 0) accent.copy(alpha = 0.8f) else RemoteBorder,
                shape = RoundedCornerShape(26.dp)
            )
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val stepPx = 28f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val downY = down.position.y
                    var accumY = 0f
                    var totalTravel = 0f
                    var didSlide = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change == null) {
                            if (!didSlide && totalTravel <= slop * 1.4f) {
                                if (downY < boxHeightPx / 2f) {
                                    latestOnUp()
                                } else {
                                    latestOnDown()
                                }
                            }
                            activeHalf = 0
                            break
                        }
                        val dy = change.positionChange().y
                        change.consume()
                        totalTravel += abs(dy)
                        accumY += dy
                        if (abs(accumY) >= stepPx) {
                            didSlide = true
                            if (accumY < 0f) {
                                activeHalf = 1
                                latestOnUp()
                            } else {
                                activeHalf = -1
                                latestOnDown()
                            }
                            accumY = 0f
                        }
                    }
                }
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = upIcon,
                contentDescription = "$label Up",
                tint = if (activeHalf == 1) accent else TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = downIcon,
                contentDescription = "$label Down",
                tint = if (activeHalf == -1) accent else TextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun MediaTransportIcon(
    icon: ImageVector,
    description: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val accentColor = MiOrange
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (accent) accentColor else RemoteSurfaceElevated,
            contentColor = if (accent) Color.White else TextPrimary
        )
    ) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}
