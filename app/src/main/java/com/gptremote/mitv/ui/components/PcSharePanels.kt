package com.gptremote.mitv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gptremote.mitv.data.RemoteRepository
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransferHistoryItem
import com.gptremote.mitv.model.TransferProgress
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
import com.gptremote.mitv.wifi.SharedPcFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PcScreenSharePanel(
    uiState: RemoteUiState,
    onStartPcMirror: () -> Unit,
    onStopPcMirror: () -> Unit,
    onTapMirroredScreen: (nx: Float, ny: Float) -> Unit,
    onMoveMirroredScreen: (nx: Float, ny: Float) -> Unit,
    onStartPhoneCast: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onOpenConnect: () -> Unit
) {
    val isWifiPc = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_PC
    var isFullscreenLandscape by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            onStopPcMirror()
        }
    }

    val context = LocalContext.current
    if (isFullscreenLandscape && uiState.pcScreenBitmap != null) {
        DisposableEffect(Unit) {
            val activity = context.findActivity()
            val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                activity?.requestedOrientation = prevOrientation
            }
        }

        val bmp = uiState.pcScreenBitmap
        Dialog(
            onDismissRequest = { isFullscreenLandscape = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(0.6f, 2.5f)
                InteractiveMirrorSurface(
                    bitmap = bmp,
                    aspect = aspect,
                    onTap = onTapMirroredScreen,
                    onMove = onMoveMirroredScreen,
                    modifier = Modifier.fillMaxSize()
                )

                FilledTonalIconButton(
                    onClick = { isFullscreenLandscape = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Exit Fullscreen")
                }

                Text(
                    text = "Tap = Click • Drag = Move Cursor",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
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
                    onClick = { isFullscreenLandscape = true },
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
private fun InteractiveMirrorSurface(
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
    onOpenConnect: () -> Unit
) {
    val isWifiPc = (uiState.status as? ConnectionStatus.Connected)?.device?.transport == TransportMode.WIFI_PC

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

        uiState.transferProgress?.let { prog ->
            TransferProgressCard(prog)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PC Shared Files", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap a file to download from PC", color = TextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onRefreshFiles, enabled = isWifiPc) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh PC Files", tint = WifiBlue)
            }
        }

        if (uiState.sharedPcFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RemoteSurfaceElevated)
                    .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = if (isWifiPc) {
                        "No files in PC Downloads/GptRemote yet. Add files in GPT Remote PC or send from phone above."
                    } else {
                        "Connect via Wi-Fi PC to browse and exchange files."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.sharedPcFiles.forEach { file ->
                    SharedFileRow(
                        file = file,
                        enabled = !uiState.isTransferringFile,
                        onClick = { onDownloadFile(file) }
                    )
                }
            }
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
    onRefreshPeerFiles: () -> Unit,
    onSendFilesToPeer: () -> Unit,
    onSendAppsToPeer: () -> Unit,
    onDownloadFromPeer: (SharedPcFile) -> Unit,
    onOpenHistoryFile: (TransferHistoryItem) -> Unit
) {
    LaunchedEffect(Unit) {
        onStartHost()
    }

    var showQrCode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        uiState.phoneTransferProgress?.let { prog ->
            TransferProgressCard(prog)
        }

        // Hotspot Helper Card (Auto Local Hotspot / System Hotspot)
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.WifiTethering, contentDescription = null, tint = WifiBlue)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.phoneHotspotSsid != null) "Hotspot Active: ${uiState.phoneHotspotSsid}" else "Wi-Fi / Direct Hotspot",
                        color = if (uiState.phoneHotspotSsid != null) ConnectedGreen else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (!uiState.phoneHotspotPassword.isNullOrBlank()) {
                            "Password: ${uiState.phoneHotspotPassword} • Join on 2nd phone then scan QR"
                        } else {
                            "Use same Wi-Fi, or start Hotspot here to connect directly"
                        },
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            if (!uiState.phoneHotspotError.isNullOrBlank()) {
                Text(uiState.phoneHotspotError, color = Color(0xFFFBBF24), fontSize = 11.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.phoneHotspotSsid != null) {
                    OutlinedButton(
                        onClick = onStopLocalHotspot,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Stop Hotspot", fontSize = 12.sp) }
                } else {
                    FilledTonalButton(
                        onClick = onStartLocalHotspot,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Start Hotspot", fontSize = 12.sp) }
                }
                OutlinedButton(
                    onClick = onOpenHotspotSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Settings", fontSize = 12.sp)
                }
            }
        }

        // Card 1: Host Files & Show/Hide QR Toggle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "This Phone Share",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add files or apps, then show QR to another phone",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                FilledTonalButton(
                    onClick = {
                        onStartHost()
                        showQrCode = !showQrCode
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showQrCode) "Hide QR" else "Show QR", fontSize = 12.sp)
                }
            }

            if (showQrCode) {
                val qrUrl = uiState.phoneHostQrUrl
                val qrBitmap = remember(qrUrl) { qrUrl?.let { generateQrBitmap(it, 420) } }
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(185.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Phone Share QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = qrUrl ?: "",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Turn on Wi-Fi or Hotspot and tap Refresh below",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddHostFiles,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onShareHostApps,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apps", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onStartHost,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Host", modifier = Modifier.size(18.dp))
                }
            }

            if (uiState.phoneHostSharedFiles.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Ready to download from this phone (${uiState.phoneHostSharedFiles.size})",
                        color = ConnectedGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    uiState.phoneHostSharedFiles.forEach { file ->
                        Text(
                            text = "• ${file.name} (${formatFileSize(file.sizeBytes)})",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Card 2: Connect to Another Phone via QR Scan
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(RemoteSurfaceElevated)
                .border(1.dp, RemoteBorder, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.peerPhoneName?.let { "Connected: $it" } ?: "Connect Other Phone",
                        color = if (uiState.peerPhoneName != null) ConnectedGreen else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan the other phone's QR to exchange files",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                FilledTonalButton(
                    onClick = onScanPeerQr,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR", fontSize = 12.sp)
                }
            }

            if (uiState.peerPhoneName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendFilesToPeer,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Send Files", color = Color(0xFF062033), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onSendAppsToPeer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apps", fontSize = 12.sp, maxLines = 1)
                    }
                    OutlinedButton(onClick = onRefreshPeerFiles, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Peer Files", modifier = Modifier.size(18.dp))
                    }
                }

                if (uiState.peerPhoneFiles.isEmpty()) {
                    Text(
                        text = "Other phone hasn't added any files yet — tap Send Files above or Add Files on the other phone.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.peerPhoneFiles.forEach { file ->
                            SharedFileRow(
                                file = file,
                                enabled = true,
                                onClick = { onDownloadFromPeer(file) }
                            )
                        }
                    }
                }
            }
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
    onScanTvCompanion: () -> Unit,
    onScanTvQr: () -> Unit,
    onConnectTvManual: (host: String, token: String) -> Unit,
    onSelectTvCompanion: (host: String, name: String, token: String) -> Unit,
    onDisconnectTv: () -> Unit,
    onRefreshTvFiles: () -> Unit,
    onSendFilesToTv: () -> Unit,
    onSendAppsToTv: () -> Unit,
    onDownloadTvFile: (SharedPcFile) -> Unit,
    onStartPhoneCastToTv: () -> Unit,
    onStopPhoneCast: () -> Unit,
    onOpenSystemCastSettings: () -> Unit,
    onOpenHistoryFile: (TransferHistoryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var showManual by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onScanTvCompanion()
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
                .heightIn(max = 680.dp)
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
                        Icon(Icons.Rounded.Tv, contentDescription = null, tint = MiOrange)
                        Column {
                            Text("Smart TV File Share & Cast", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Requires GPT Remote TV app on TV (or System Cast)", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                // Connected TV Companion status or scanner
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
                            Text("${activeTv.host} • Ready for Files, APKs & Live Cast", color = TextSecondary, fontSize = 11.sp)
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

                // Live Cast section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RemoteSurfaceElevated)
                        .border(1.dp, RemoteBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Screen Cast to TV", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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

                // TV File Transfer Progress
                uiState.tvTransferProgress?.let { prog ->
                    TransferProgressCard(prog)
                }

                // Send Files & APKs to TV
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
                    OutlinedButton(onClick = onRefreshTvFiles, enabled = activeTv != null, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh TV Files", modifier = Modifier.size(18.dp))
                    }
                }

                if (uiState.tvCompanionFiles.isNotEmpty()) {
                    Text("Files stored on TV (${uiState.tvCompanionFiles.size})", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    uiState.tvCompanionFiles.take(10).forEach { file ->
                        SharedFileRow(file = file, enabled = true, onClick = { onDownloadTvFile(file) })
                    }
                }

                TransferHistorySection(
                    title = "TV Transfer History",
                    items = uiState.tvTransferHistory,
                    onOpenItem = onOpenHistoryFile
                )
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RemoteSurfaceElevated)
                                .clickable { selectedMap[app.packageName] = !checked }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selectedMap[app.packageName] = it }
                            )
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
fun TransferProgressCard(prog: TransferProgress) {
    val total = prog.totalBytes.coerceAtLeast(1L)
    val frac = (prog.bytesDone.toFloat() / total).coerceIn(0f, 1f)
    val pct = (frac * 100f).roundToInt()
    val accent = if (prog.isComplete) ConnectedGreen else MiOrange

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
                text = if (prog.isUpload) "Sending: ${prog.fileName}" else "Receiving: ${prog.fileName}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (prog.isComplete) "Done" else "$pct%",
                color = accent,
                fontSize = 13.sp,
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatFileSize(prog.bytesDone)} / ${formatFileSize(prog.totalBytes)}",
                color = TextSecondary,
                fontSize = 11.sp
            )
            if (prog.bytesPerSec > 0L) {
                Text(
                    text = formatTransferSpeed(prog.bytesPerSec),
                    color = WifiBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

    // 0 = All, 1 = Sent, 2 = Received
    var filterMode by remember { mutableIntStateOf(0) }
    val sentItems = remember(items) { items.filter { it.isSent } }
    val recvItems = remember(items) { items.filter { !it.isSent } }
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
                val tag = if (entry.isSent) "Sent" else "Received"
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
                        Icon(
                            imageVector = if (entry.fileName.lowercase(Locale.US).endsWith(".apk")) {
                                Icons.Rounded.Android
                            } else {
                                Icons.AutoMirrored.Rounded.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (entry.isSent) MiOrange else ConnectedGreen
                        )
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
                                text = "$tag at $timeStr • ${formatFileSize(entry.sizeBytes)}$spdTxt",
                                color = TextSecondary,
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = WifiBlue)
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
