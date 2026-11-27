package com.gptremote.mitv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransferProgress
import com.gptremote.mitv.model.TransferQueueItem
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBg
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue
import com.gptremote.mitv.wifi.DiscoveredDlnaTv
import com.gptremote.mitv.wifi.HostedMediaItem
import com.gptremote.mitv.wifi.SharedPcFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

private val AppIconMemoryCache = ConcurrentHashMap<String, ImageBitmap>()

enum class TvSheetMode {
    FILE_SHARE,
    SCREEN_CAST
}

@Composable
fun PcScreenSharePanel(
    uiState: RemoteUiState,
    onStartPcMirror: () -> Unit,
    onStopPcMirror: () -> Unit,
    onTapMirroredScreen: (nx: Float, ny: Float) -> Unit,
    onMoveMirroredScreen: (nx: Float, ny: Float) -> Unit,
    onOpenFullscreenMirror: () -> Unit = {},
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onOpenConnect: () -> Unit
) {
    val isWifiPc = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_PC

    DisposableEffect(Unit) {
        onDispose {
            onStopPcMirror()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isWifiPc) {
            WifiPcRequiredBanner(onOpenConnect = onOpenConnect)
        }

        // 1. View & Touch-Control PC Screen on Phone
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.DesktopWindows, contentDescription = null, tint = WifiBlue)
                    Column {
                        Text("PC Screen on Phone", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap to open/click • Drag to move cursor", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                if (uiState.isMirroringPc) {
                    OutlinedButton(onClick = onStopPcMirror, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onStartPcMirror,
                        enabled = isWifiPc,
                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("View PC", fontSize = 12.sp)
                    }
                }
            }

            val bmp = uiState.pcScreenBitmap
            if (bmp != null) {
                val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(0.6f, 2.5f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, ConnectedGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                ) {
                    InteractiveMirrorSurface(
                        bitmap = bmp,
                        aspect = aspect,
                        onTap = onTapMirroredScreen,
                        onMove = onMoveMirroredScreen,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                FilledTonalButton(
                    onClick = onOpenFullscreenMirror,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fullscreen Landscape View", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RemoteSurface)
                        .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isMirroringPc) {
                            "Loading PC screen stream..."
                        } else {
                            "Tap 'View PC' to mirror & control PC screen here"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Cast Phone Screen to PC Window
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isCastingPhoneScreen) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                        contentDescription = null,
                        tint = if (uiState.isCastingPhoneScreen) ConnectedGreen else MiOrange
                    )
                    Column {
                        Text("Phone Screen to PC", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (uiState.isCastingPhoneScreen) "Casting live to GPT Remote PC" else "Mirror phone display on PC",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                if (uiState.isCastingPhoneScreen) {
                    OutlinedButton(onClick = onStopPhoneCast, shape = RoundedCornerShape(12.dp)) {
                        Text("Stop Cast", fontSize = 12.sp, color = Color(0xFFF87171))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onStartPhoneCast,
                        enabled = isWifiPc,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cast Screen", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveMirrorSurface(
    bitmap: android.graphics.Bitmap,
    aspect: Float,
    onTap: (nx: Float, ny: Float) -> Unit,
    onMove: (nx: Float, ny: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnMove by rememberUpdatedState(onMove)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boxAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else aspect
        val (targetW, targetH) = if (boxAspect > aspect) {
            (maxHeight * aspect) to maxHeight
        } else {
            maxWidth to (maxWidth / aspect)
        }

        Box(
            modifier = Modifier
                .size(width = targetW, height = targetH)
                .pointerInput(bitmap.width, bitmap.height) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var totalTravel = Offset.Zero
                        var movedBeyondTap = false
                        var lastMoveEmit = 0L

                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            if (active.isEmpty()) {
                                if (!movedBeyondTap) {
                                    val nx = (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    latestOnTap(nx, ny)
                                }
                                break
                            }
                            val p = active.first()
                            val d = p.positionChange()
                            p.consume()
                            totalTravel += Offset(abs(d.x), abs(d.y))
                            if (totalTravel.getDistance() > slop) {
                                movedBeyondTap = true
                            }
                            if (movedBeyondTap && d != Offset.Zero) {
                                val now = System.currentTimeMillis()
                                if (now - lastMoveEmit >= 20L) {
                                    lastMoveEmit = now
                                    val nx = (p.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val ny = (p.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    latestOnMove(nx, ny)
                                }
                            }
                        }
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Mirrored PC Screen",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PcFileSharePanel(
    uiState: RemoteUiState,
    onRefreshFiles: () -> Unit,
    onPickFilesToSend: () -> Unit,
    onShareAppsToPc: () -> Unit,
    onDownloadFile: (SharedPcFile) -> Unit,
    onOpenHistoryFile: (TransferHistoryItem) -> Unit,
    onPauseTransfer: () -> Unit = {},
    onResumeTransfer: () -> Unit = {},
    onCancelTransfer: () -> Unit = {},
    onOpenConnect: () -> Unit
) {
    val isWifiPc = uiState.connectedWifiPcDevice != null ||
        (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_PC

    LaunchedEffect(isWifiPc) {
        if (isWifiPc) {
            onRefreshFiles()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isWifiPc) {
            WifiPcRequiredBanner(onOpenConnect = onOpenConnect)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPickFilesToSend,
                enabled = isWifiPc && !uiState.isTransferringFile,
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isTransferringFile) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isTransferringFile) "Sending..." else "Send Files",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            FilledTonalButton(
                onClick = onShareAppsToPc,
                enabled = isWifiPc && !uiState.isTransferringFile,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Apps (.apk)", fontSize = 12.sp, maxLines = 1)
            }
        }

        uiState.transferProgress?.let { prog ->
            TransferProgressCard(
                prog = prog,
                queueItems = uiState.pcTransferQueue,
                onPause = onPauseTransfer,
                onResume = onResumeTransfer,
                onCancel = onCancelTransfer
            )
        }

        TransferHistorySection(
            title = "PC Transfer History",
            items = uiState.pcTransferHistory,
            onOpenItem = onOpenHistoryFile
        )
    }
}

@Composable
fun PhoneSharePanel(
    uiState: RemoteUiState,
    onStartHost: () -> Unit,
    onStartLocalHotspot: () -> Unit,
    onStopLocalHotspot: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onAddHostFiles: () -> Unit,
    onShareHostApps: () -> Unit,
    onScanPeerQr: () -> Unit,
    onDisconnectPeer: () -> Unit = {},
    onRefreshPeerFiles: () -> Unit,
    onSendFilesToPeer: () -> Unit,
    onSendAppsToPeer: () -> Unit,
    onDownloadFromPeer: (SharedPcFile) -> Unit,
    onOpenHistoryFile: (TransferHistoryItem) -> Unit,
    onPauseTransfer: () -> Unit = {},
    onResumeTransfer: () -> Unit = {},
    onCancelTransfer: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onStartHost()
    }

    var activeMode by remember { mutableStateOf("send") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary Send / Receive Selector Row (No idle hotspot clutter at top)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val sendSelected = activeMode == "send"
            Button(
                onClick = { activeMode = "send" },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sendSelected) MiOrange else RemoteSurfaceElevated,
                    contentColor = if (sendSelected) Color.White else TextPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            val recvSelected = activeMode == "receive"
            Button(
                onClick = {
                    activeMode = "receive"
                    onStartHost()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recvSelected) WifiBlue else RemoteSurfaceElevated,
                    contentColor = if (recvSelected) Color(0xFF062033) else TextPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Receive", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (activeMode == "send") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.peerPhoneName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected: ${uiState.peerPhoneName}",
                                color = ConnectedGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ready to send files or apps",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = onScanPeerQr, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch", fontSize = 11.sp)
                            }
                            TextButton(onClick = onDisconnectPeer) {
                                Text("Off", color = Color(0xFFF87171), fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSendFilesToPeer,
                            modifier = Modifier.weight(1.25f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Files", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onSendAppsToPeer,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apps (.apk)", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                } else {
                    Text(
                        text = "Send Files to Phone",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap Receive on the other phone, then scan its QR code",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = onScanPeerQr,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Receiver QR", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = if (uiState.phoneHotspotSsid != null) onStopLocalHotspot else onStartLocalHotspot,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.phoneHotspotSsid != null) "Hotspot On" else "Hotspot", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenHotspotSettings,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wi-Fi Settings", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Receive mode: Show QR + inline Hotspot / Wi-Fi toggle when needed
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (uiState.peerPhoneName != null) "Connected: ${uiState.peerPhoneName}" else "Receive Files on This Phone",
                    color = if (uiState.peerPhoneName != null) ConnectedGreen else TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                val qrUrl = uiState.phoneHostQrUrl
                val qrBitmap = remember(qrUrl) { qrUrl?.let { generateQrBitmap(it, 420) } }
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Receive Files QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "Scan this QR from the sender phone's Send tab",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    Text(
                        text = "Connect to Wi-Fi or turn on Hotspot below to generate your Receive QR",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp
                    )
                }
                if (uiState.phoneHotspotSsid != null) {
                    Text(
                        text = "Hotspot Active: ${uiState.phoneHotspotSsid} • Pass: ${uiState.phoneHotspotPassword.orEmpty()}",
                        color = ConnectedGreen,
                        fontSize = 11.sp
                    )
                }
                if (!uiState.phoneHotspotError.isNullOrBlank()) {
                    Text(uiState.phoneHotspotError, color = Color(0xFFFBBF24), fontSize = 11.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = if (uiState.phoneHotspotSsid != null) onStopLocalHotspot else onStartLocalHotspot,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (uiState.phoneHotspotSsid != null) "Stop Hotspot" else "Start Hotspot", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenHotspotSettings,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Wi-Fi Settings", fontSize = 12.sp)
                    }
                }
            }
        }

        uiState.phoneTransferProgress?.let { prog ->
            TransferProgressCard(
                prog = prog,
                queueItems = uiState.phoneTransferQueue,
                onPause = onPauseTransfer,
                onResume = onResumeTransfer,
                onCancel = onCancelTransfer
            )
        }

        TransferHistorySection(
            title = "Phone Transfer History",
            items = uiState.phoneTransferHistory,
            onOpenItem = onOpenHistoryFile
        )
    }
}

@Composable
fun TvCompanionSheetDialog(
    uiState: RemoteUiState,
    sheetMode: TvSheetMode = TvSheetMode.FILE_SHARE,
    preferNonAndroidTab: Boolean = false,
    onScanTvCompanion: () -> Unit,
    onScanTvQr: () -> Unit,
    onConnectTvManual: (host: String, token: String) -> Unit,
    onSelectTvCompanion: (host: String, name: String, token: String) -> Unit,
    onDisconnectTv: () -> Unit,
    onRefreshTvFiles: () -> Unit,
    onSendFilesToTv: () -> Unit,
    onSendAppsToTv: () -> Unit,
    onDownloadTvFile: (SharedPcFile) -> Unit,
    onPauseTvTransfer: () -> Unit = {},
    onResumeTvTransfer: () -> Unit = {},
    onCancelTvTransfer: () -> Unit = {},
    onStartPhoneCastToTv: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onOpenSystemCastSettings: () -> Unit,
    onStartTvBrowserHost: () -> Unit,
    onAddBrowserDlnaFiles: () -> Unit,
    onOpenInTvBrowser: () -> Unit,
    onScanDlna: () -> Unit,
    onSelectDlnaRenderer: (DiscoveredDlnaTv) -> Unit,
    onCastMediaDlna: (HostedMediaItem) -> Unit,
    onDlnaPlaybackAction: (String) -> Unit,
    onOpenHistoryFile: (TransferHistoryItem) -> Unit,
    onDismiss: () -> Unit
) {
    val modeTab = if (preferNonAndroidTab && uiState.connectedTvCompanion == null) 1 else 0
    var showManual by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var showBrowserQr by remember { mutableStateOf(false) }

    LaunchedEffect(sheetMode) {
        onScanTvCompanion()
        if (sheetMode == TvSheetMode.FILE_SHARE) onStartTvBrowserHost()
        if (preferNonAndroidTab || sheetMode == TvSheetMode.SCREEN_CAST) onScanDlna()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = RemoteBg,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .heightIn(max = 700.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (sheetMode == TvSheetMode.SCREEN_CAST) Icons.Rounded.Cast else Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = MiOrange
                        )
                        Column {
                            Text(
                                text = if (sheetMode == TvSheetMode.SCREEN_CAST) "Screen Cast to TV" else "Share Files to TV",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (modeTab == 0) "Android TV / Google TV App" else "Samsung / LG / Any TV (No TV App Needed)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                if (modeTab == 0) {
                    val activeTv = uiState.connectedTvCompanion
                    if (activeTv != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(RemoteSurfaceElevated)
                                .border(1.dp, ConnectedGreen.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TV Connected: ${activeTv.name}", color = ConnectedGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (sheetMode == TvSheetMode.SCREEN_CAST) "${activeTv.host} • Ready for Live Cast" else "${activeTv.host} • Ready for Files & APKs",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = onDisconnectTv) {
                                Text("Off", color = Color(0xFFF87171))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onScanTvCompanion,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.isScanningWifiPc) "Scanning TVs..." else "Scan Wi-Fi TV", fontSize = 12.sp)
                            }
                            Button(
                                onClick = onScanTvQr,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan TV QR", fontSize = 12.sp)
                            }
                        }

                        if (uiState.discoveredTvCompanions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.discoveredTvCompanions.forEach { tv ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(RemoteSurfaceElevated)
                                            .border(1.dp, RemoteBorder, RoundedCornerShape(12.dp))
                                            .clickable { onSelectTvCompanion(tv.host, tv.name, tv.osName) }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tv.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("${tv.host} • GPT Remote TV Companion", color = WifiBlue, fontSize = 11.sp)
                                        }
                                        Text("Connect", color = MiOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        TextButton(onClick = { showManual = !showManual }) {
                            Text(
                                if (showManual) "Hide Manual TV IP" else "Enter TV IP & Pair Code Manually",
                                color = WifiBlue,
                                fontSize = 12.sp
                            )
                        }
                        if (showManual) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = manualHost,
                                    onValueChange = { manualHost = it },
                                    label = { Text("TV IP") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.3f)
                                )
                                OutlinedTextField(
                                    value = manualCode,
                                    onValueChange = { manualCode = it },
                                    label = { Text("Pair Code") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { onConnectTvManual(manualHost, manualCode) },
                                    enabled = manualHost.isNotBlank() && manualCode.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
                                ) { Text("Go") }
                            }
                        }
                    }

                    if (sheetMode == TvSheetMode.SCREEN_CAST) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RemoteSurfaceElevated)
                                .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Mirror Phone Display to TV", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "Cast directly to GPT Remote TV app or open system Wireless Display / Smart View.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.isCastingToTv) {
                                    OutlinedButton(
                                        onClick = onStopPhoneCast,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Stop TV Cast", color = Color(0xFFF87171), fontSize = 12.sp) }
                                } else {
                                    Button(
                                        onClick = onStartPhoneCastToTv,
                                        enabled = activeTv != null,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Cast, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF062033))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cast via App", color = Color(0xFF062033), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = onOpenSystemCastSettings,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Wireless Cast", fontSize = 12.sp, maxLines = 1) }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSendFilesToTv,
                                enabled = activeTv != null,
                                modifier = Modifier.weight(1.2f),
                                colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = onSendAppsToTv,
                                enabled = activeTv != null,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apps (.apk)", fontSize = 12.sp)
                            }
                        }

                        uiState.tvTransferProgress?.let { prog ->
                            TransferProgressCard(
                                prog = prog,
                                queueItems = uiState.tvTransferQueue,
                                onPause = onPauseTvTransfer,
                                onResume = onResumeTvTransfer,
                                onCancel = onCancelTvTransfer
                            )
                        }

                        TransferHistorySection(
                            title = "TV Transfer History",
                            items = uiState.tvTransferHistory,
                            onOpenItem = onOpenHistoryFile
                        )
                    }
                } else {
                    if (sheetMode == TvSheetMode.SCREEN_CAST) {
                        Button(
                            onClick = onOpenSystemCastSettings,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Cast, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Wireless Screen Mirroring (Smart View / Miracast)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "Or use DLNA TV Cast below to stream videos & photos directly to Samsung/LG TV.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Button(
                            onClick = onAddBrowserDlnaFiles,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Photos / Videos / Files to Share", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    // DLNA Media Push
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RemoteSurfaceElevated)
                            .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DLNA TV Player (Samsung / LG / Sony)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Pushes photos, videos & music straight to TV", color = TextSecondary, fontSize = 11.sp)
                            }
                            FilledTonalButton(onClick = onScanDlna, shape = RoundedCornerShape(12.dp)) {
                                if (uiState.isScanningDlna) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (uiState.isScanningDlna) "Scanning" else "Scan TVs", fontSize = 11.sp)
                            }
                        }

                        if (uiState.dlnaRenderers.isEmpty()) {
                            Text(
                                text = "Tap Scan TVs on the same Wi-Fi to detect Samsung/LG DLNA TVs.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        } else {
                            uiState.dlnaRenderers.forEach { dlnaTv ->
                                val selected = uiState.activeDlnaRenderer?.id == dlnaTv.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Color(0xFF132C3E) else RemoteSurface)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) WifiBlue else RemoteBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onSelectDlnaRenderer(dlnaTv) }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dlnaTv.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("${dlnaTv.manufacturer} • ${dlnaTv.host}", color = TextSecondary, fontSize = 10.sp)
                                    }
                                    Text(
                                        text = if (selected) "Selected" else "Use TV",
                                        color = if (selected) ConnectedGreen else WifiBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (uiState.activeDlnaRenderer != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = { onDlnaPlaybackAction("Play") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play", fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = { onDlnaPlaybackAction("Pause") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause", fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = { onDlnaPlaybackAction("Stop") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (uiState.phoneHostedMediaItems.isNotEmpty()) {
                        Text("Ready to View / Cast on TV (${uiState.phoneHostedMediaItems.size})", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        uiState.phoneHostedMediaItems.take(12).forEach { media ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RemoteSurfaceElevated)
                                    .border(1.dp, RemoteBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FileThumbnailBadge(fileName = media.name, uriString = null, size = 34.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(media.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${formatFileSize(media.sizeBytes)} • ${media.mimeType}", color = TextSecondary, fontSize = 10.sp, maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { onCastMediaDlna(media) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Cast, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cast TV", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (sheetMode == TvSheetMode.FILE_SHARE) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RemoteSurfaceElevated)
                                .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = WifiBlue)
                                Column {
                                    Text("TV Web Browser Link (All Smart TVs)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = uiState.tvBrowserShareUrl ?: "Turn on Wi-Fi or Hotspot for TV link",
                                        color = WifiBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Open that address in your Samsung/LG/Hisense TV Browser to view photos, play videos, or download files.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenInTvBrowser,
                                    modifier = Modifier.weight(1.2f),
                                    colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Open on TV", color = Color(0xFF062033), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                OutlinedButton(
                                    onClick = { showBrowserQr = !showBrowserQr },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (showBrowserQr) "Hide QR" else "Browser QR", fontSize = 11.sp)
                                }
                            }
                            if (showBrowserQr && uiState.tvBrowserShareUrl != null) {
                                val qrBmp = remember(uiState.tvBrowserShareUrl) { generateQrBitmap(uiState.tvBrowserShareUrl, 360) }
                                if (qrBmp != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(150.dp)
                                            .align(Alignment.CenterHorizontally)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .padding(6.dp)
                                    ) {
                                        Image(bitmap = qrBmp.asImageBitmap(), contentDescription = "TV Browser URL QR", modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledAppsPickerDialog(
    apps: List<RemoteRepository.ShareableAppInfo>,
    onConfirm: (List<RemoteRepository.ShareableAppInfo>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val selectedMap = remember { mutableStateMapOf<String, Boolean>() }
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val chosenApps = apps.filter { selectedMap[it.packageName] == true }

    // Pre-warm app icon cache on background threads so scrolling feels instant
    LaunchedEffect(apps) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            apps.forEach { app ->
                if (!AppIconMemoryCache.containsKey(app.packageName)) {
                    runCatching {
                        val bmp = pm.getApplicationIcon(app.packageName).toBitmap(72, 72).asImageBitmap()
                        AppIconMemoryCache[app.packageName] = bmp
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = RemoteBg,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Share Installed Apps (.apk)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = selectedMap[app.packageName] == true
                        val pm = context.packageManager
                        val iconBmp by produceState<ImageBitmap?>(
                            initialValue = AppIconMemoryCache[app.packageName],
                            key1 = app.packageName
                        ) {
                            if (value == null) {
                                value = withContext(Dispatchers.IO) {
                                    AppIconMemoryCache[app.packageName] ?: runCatching {
                                        pm.getApplicationIcon(app.packageName).toBitmap(72, 72).asImageBitmap()
                                    }.getOrNull()?.also { bmp ->
                                        AppIconMemoryCache[app.packageName] = bmp
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RemoteSurfaceElevated)
                                .clickable { selectedMap[app.packageName] = !checked }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selectedMap[app.packageName] = it }
                            )
                            if (iconBmp != null) {
                                Image(
                                    bitmap = iconBmp!!,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(RemoteSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Android, contentDescription = null, tint = ConnectedGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatFileSize(app.sizeBytes)} • ${app.packageName}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onConfirm(chosenApps) },
                    enabled = chosenApps.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Share ${chosenApps.size} Selected App(s)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun TransferProgressCard(
    prog: TransferProgress,
    queueItems: List<TransferQueueItem> = emptyList(),
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    showControls: Boolean = true
) {
    val total = prog.totalBytes.coerceAtLeast(1L)
    val frac = (prog.bytesDone.toFloat() / total).coerceIn(0f, 1f)
    val pct = (frac * 100f).roundToInt()
    val accent = when {
        prog.isComplete -> ConnectedGreen
        prog.isPaused -> Color(0xFFFBBF24)
        else -> MiOrange
    }
    var showQueueModal by remember { mutableStateOf(false) }

    if (showQueueModal && queueItems.isNotEmpty()) {
        Dialog(onDismissRequest = { showQueueModal = false }) {
            Surface(
                color = RemoteSurfaceElevated,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sending Queue (${queueItems.size})", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Live batch status", color = TextSecondary, fontSize = 11.sp)
                        }
                        IconButton(onClick = { showQueueModal = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Queue", tint = TextSecondary)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(queueItems, key = { it.id }) { q ->
                            val stColor = when (q.status) {
                                "Sending" -> MiOrange
                                "Paused" -> Color(0xFFFBBF24)
                                "Sent" -> ConnectedGreen
                                "Cancelled", "Failed" -> Color(0xFFF87171)
                                else -> TextSecondary
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RemoteSurface)
                                    .border(1.dp, RemoteBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FileThumbnailBadge(fileName = q.fileName, uriString = null, size = 30.dp)
                                    Column {
                                        Text(
                                            text = q.fileName,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(formatFileSize(q.sizeBytes), color = TextSecondary, fontSize = 10.sp)
                                    }
                                }
                                Text(
                                    text = q.status,
                                    color = stColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    prog.isPaused -> "Paused: ${prog.fileName}"
                    prog.isUpload -> "Sending: ${prog.fileName}"
                    else -> "Receiving: ${prog.fileName}"
                },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (queueItems.size > 1) {
                Text(
                    text = "View All (${queueItems.size})",
                    color = WifiBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showQueueModal = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = when {
                    prog.isComplete -> "Done"
                    prog.isPaused -> "Paused • $pct%"
                    else -> "$pct%"
                },
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = accent,
            trackColor = RemoteSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${formatFileSize(prog.bytesDone)} / ${formatFileSize(prog.totalBytes)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (prog.bytesPerSec > 0L && !prog.isPaused) {
                    Text(
                        text = formatTransferSpeed(prog.bytesPerSec),
                        color = WifiBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (showControls && !prog.isComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = if (prog.isPaused) onResume else onPause,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (prog.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (prog.isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (prog.isPaused) "Resume" else "Pause", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFF87171), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TransferHistorySection(
    title: String,
    items: List<TransferHistoryItem>,
    onOpenItem: (TransferHistoryItem) -> Unit
) {
    if (items.isEmpty()) return

    var filterMode by remember { mutableIntStateOf(0) }
    val sentItems = remember(items) { items.filter { it.isSent && it.statusLabel == "Sent" } }
    val recvItems = remember(items) { items.filter { !it.isSent && it.statusLabel == "Received" } }
    val shown = when (filterMode) {
        1 -> sentItems
        2 -> recvItems
        else -> items
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text("All (${items.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text("Sent (${sentItems.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text("Received (${recvItems.size})", fontSize = 11.sp) }
            )
        }

        if (shown.isEmpty()) {
            Text(
                text = "No files in this tab yet",
                color = TextSecondary,
                fontSize = 12.sp
            )
        } else {
            shown.forEach { entry ->
                val timeStr = remember(entry.timestampMs) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.timestampMs))
                }
                val tagColor = when (entry.statusLabel) {
                    "Failed" -> Color(0xFFF87171)
                    "Cancelled" -> Color(0xFFFBBF24)
                    "Received" -> ConnectedGreen
                    else -> MiOrange
                }
                val spdTxt = if (entry.bytesPerSec > 0L) " • ${formatTransferSpeed(entry.bytesPerSec)}" else ""

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RemoteSurfaceElevated)
                        .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp))
                        .clickable { onOpenItem(entry) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        FileThumbnailBadge(fileName = entry.fileName, uriString = entry.localUriString, size = 38.dp)
                        Column {
                            Text(
                                text = entry.fileName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${entry.statusLabel} at $timeStr • ${formatFileSize(entry.sizeBytes)}$spdTxt",
                                color = if (entry.statusLabel == "Failed" || entry.statusLabel == "Cancelled") tagColor else TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open ${entry.fileName}",
                        tint = WifiBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedFileRow(
    file: SharedPcFile,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, RemoteBorder, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            FileThumbnailBadge(fileName = file.name, uriString = null, size = 36.dp)
            Column {
                Text(
                    text = file.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(file.sizeBytes),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.CloudDownload,
            contentDescription = "Download ${file.name}",
            tint = ConnectedGreen
        )
    }
}

private object ThumbnailMemoryCache {
    val cache = LruCache<String, ImageBitmap>(48)
}

@Composable
private fun FileThumbnailBadge(
    fileName: String,
    uriString: String?,
    size: Dp = 36.dp
) {
    val ext = remember(fileName) { fileName.substringAfterLast('.', "").lowercase(Locale.US) }
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    val isVideo = ext in listOf("mp4", "mkv", "mov", "webm", "avi", "m4v")
    val context = LocalContext.current

    val thumb by produceState<ImageBitmap?>(
        initialValue = if (!uriString.isNullOrBlank()) ThumbnailMemoryCache.cache.get(uriString) else null,
        uriString,
        ext
    ) {
        if (uriString.isNullOrBlank() || (!isImage && !isVideo)) {
            value = null
            return@produceState
        }
        val cached = ThumbnailMemoryCache.cache.get(uriString)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val loaded = runCatching {
                val uri = Uri.parse(uriString)
                var bmp: Bitmap? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bmp = runCatching { context.contentResolver.loadThumbnail(uri, Size(128, 128), null) }.getOrNull()
                }
                if (bmp == null && isVideo) {
                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(context, uri)
                        val frame = mmr.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: mmr.frameAtTime
                        if (frame != null) {
                            bmp = ThumbnailUtils.extractThumbnail(frame, 112, 112, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                        }
                    } finally {
                        runCatching { mmr.release() }
                    }
                } else if (bmp == null && isImage) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        BitmapFactory.decodeStream(inp, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        BitmapFactory.decodeStream(inp, null, opts)?.let { decoded ->
                            bmp = ThumbnailUtils.extractThumbnail(decoded, 112, 112, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                        }
                    }
                }
                bmp?.asImageBitmap()
            }.getOrNull()
            if (loaded != null) {
                ThumbnailMemoryCache.cache.put(uriString, loaded)
            }
            loaded
        }
    }

    if (thumb != null) {
        Image(
            bitmap = thumb!!,
            contentDescription = fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, RemoteBorder, RoundedCornerShape(10.dp))
        )
    } else {
        val (vec, tint) = when {
            ext == "apk" -> Icons.Rounded.Android to ConnectedGreen
            isVideo -> Icons.Rounded.Movie to WifiBlue
            isImage -> Icons.Rounded.Image to Color(0xFFF472B6)
            ext == "pdf" -> Icons.Rounded.PictureAsPdf to Color(0xFFF87171)
            ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.FolderZip to Color(0xFFFBBF24)
            ext in listOf("mp3", "wav", "m4a", "aac", "flac", "ogg") -> Icons.Rounded.AudioFile to Color(0xFF2DD4BF)
            else -> Icons.AutoMirrored.Rounded.InsertDriveFile to WifiBlue
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.15f))
                .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(vec, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
        }
    }
}

@Composable
private fun WifiPcRequiredBanner(onOpenConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RemoteSurfaceElevated)
            .border(1.dp, WifiBlue.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Connect GPT Remote PC (Wi-Fi)", color = WifiBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Screen sharing and file transfer use the GPT Remote PC Windows app over Wi-Fi (or scan its QR code).",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Button(
            onClick = onOpenConnect,
            colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Connect Wi-Fi PC", color = Color(0xFF062033), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun formatTransferSpeed(bps: Long): String = when {
    bps >= 1024L * 1024L -> String.format("%.1f MB/s", bps / (1024.0 * 1024.0))
    bps > 0L -> "${maxOf(1L, bps / 1024L)} KB/s"
    else -> ""
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
