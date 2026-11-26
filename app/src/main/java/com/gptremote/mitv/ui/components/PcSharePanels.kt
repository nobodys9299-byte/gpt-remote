package com.gptremote.mitv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gptremote.mitv.model.ConnectionStatus
import com.gptremote.mitv.model.RemoteUiState
import com.gptremote.mitv.model.TransferProgress
import com.gptremote.mitv.model.TransportMode
import com.gptremote.mitv.ui.theme.ConnectedGreen
import com.gptremote.mitv.ui.theme.MiOrange
import com.gptremote.mitv.ui.theme.RemoteBorder
import com.gptremote.mitv.ui.theme.RemoteSurface
import com.gptremote.mitv.ui.theme.RemoteSurfaceElevated
import com.gptremote.mitv.ui.theme.TextPrimary
import com.gptremote.mitv.ui.theme.TextSecondary
import com.gptremote.mitv.ui.theme.WifiBlue
import com.gptremote.mitv.wifi.SharedPcFile
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
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(0.6f, 2.5f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .pointerInput(bmp.width, bmp.height) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                onTapMirroredScreen(nx, ny)
                            }
                        }
                        .pointerInput(bmp.width, bmp.height) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                onMoveMirroredScreen(nx, ny)
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Fullscreen PC Screen",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                FilledTonalIconButton(
                    onClick = { isFullscreenLandscape = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Exit Fullscreen")
                }
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
                        .pointerInput(bmp.width, bmp.height) {
                            detectTapGestures { offset ->
                                val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                onTapMirroredScreen(nx, ny)
                            }
                        }
                        .pointerInput(bmp.width, bmp.height) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val nx = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                onMoveMirroredScreen(nx, ny)
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Live PC Screen",
                        contentScale = ContentScale.FillBounds,
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
fun PcFileSharePanel(
    uiState: RemoteUiState,
    onRefreshFiles: () -> Unit,
    onPickFileToSend: () -> Unit,
    onDownloadFile: (SharedPcFile) -> Unit,
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

        Button(
            onClick = onPickFileToSend,
            enabled = isWifiPc && !uiState.isTransferringFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isTransferringFile) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isTransferringFile) "Transferring..." else "Send File from Phone to PC",
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PC Shared Files", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Files in PC Downloads/GptRemote", color = TextSecondary, fontSize = 11.sp)
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
    }
}

@Composable
fun PhoneSharePanel(
    uiState: RemoteUiState,
    onStartHost: () -> Unit,
    onAddHostFile: () -> Unit,
    onScanPeerQr: () -> Unit,
    onRefreshPeerFiles: () -> Unit,
    onSendToPeer: () -> Unit,
    onDownloadFromPeer: (SharedPcFile) -> Unit
) {
    LaunchedEffect(Unit) {
        onStartHost()
    }

    var showScanner by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        uiState.phoneTransferProgress?.let { prog ->
            TransferProgressCard(prog)
        }

        // Card 1: Show My Phone QR & Shared Files
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
            Text(
                text = "This Phone QR Code",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Another phone running GPT Remote can scan this QR to send or download files over Wi-Fi / Hotspot",
                color = TextSecondary,
                fontSize = 11.sp
            )

            val qrUrl = uiState.phoneHostQrUrl
            val qrBitmap = remember(qrUrl) { qrUrl?.let { generateQrBitmap(it, 420) } }
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
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
            } else {
                Text(
                    text = "Turn on Wi-Fi or Mobile Hotspot and tap Refresh Host",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddHostFile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MiOrange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add File", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onStartHost,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh QR", modifier = Modifier.size(18.dp))
                }
            }

            if (uiState.phoneHostSharedFiles.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Ready on this phone (${uiState.phoneHostSharedFiles.size})",
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
                    onClick = { showScanner = true },
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSendToPeer,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = WifiBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Send File to Phone", color = Color(0xFF062033), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = onRefreshPeerFiles, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Peer Files", modifier = Modifier.size(18.dp))
                    }
                }

                if (uiState.peerPhoneFiles.isEmpty()) {
                    Text(
                        text = "Other phone hasn't added any files yet — tap Send File above or Add File on the other phone.",
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
    }

    if (showScanner) {
        onScanPeerQr()
        showScanner = false
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
        Text(
            text = "${formatFileSize(prog.bytesDone)} / ${formatFileSize(prog.totalBytes)}",
            color = TextSecondary,
            fontSize = 11.sp
        )
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
